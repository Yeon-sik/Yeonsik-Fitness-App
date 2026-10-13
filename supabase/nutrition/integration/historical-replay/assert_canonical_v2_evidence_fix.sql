do $assert_canonical_v2_evidence_fix$
declare
    v_versions text[];
    v_target regprocedure;
    v_before_definition text;
    v_before_privileges text;
    v_expected_definition text;
    v_anchor text := '(select estimation_evidence_id from public.nutrition_verified_imports where id = v_existing.projection_import_id)';
    v_qualified text := '(select verified_import.estimation_evidence_id from public.nutrition_verified_imports as verified_import where verified_import.id = v_existing.projection_import_id)';
    v_anchor_count integer;
    v_public_function_count integer;
    v_before_function_count integer;
    v_before_data record;
    v_after_rows jsonb;
begin
    select array_agg(m.version::text order by m.version)
    into v_versions
    from supabase_migrations.schema_migrations as m;
    if cardinality(v_versions) <> 35
       or not ('20261013065001' = any(v_versions))
       or not ('20261013072000' = any(v_versions)) then
        raise exception 'Expected 28 recovered migrations and seven forward migrations including the guards and v2 repair, got %', v_versions;
    end if;

    v_target := pg_catalog.to_regprocedure(
        'public.import_canonical_nutrition_v2(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb)'
    );
    if v_target is null then
        raise exception 'Exact canonical v2 RPC signature is missing';
    end if;

    select before_row.definition, before_row.privileges
    into v_before_definition, v_before_privileges
    from nutrition_v2_evidence_fix_replay.public_functions_before_fix as before_row
    where before_row.function_oid = v_target::oid
      and before_row.function_name = 'import_canonical_nutrition_v2';
    if v_before_definition is null then
        raise exception 'Pre-fix snapshot of the exact canonical v2 RPC is missing';
    end if;

    v_anchor_count := (
        pg_catalog.length(v_before_definition)
        - pg_catalog.length(pg_catalog.replace(v_before_definition, v_anchor, ''))
    ) / pg_catalog.length(v_anchor);
    if v_anchor_count <> 1 then
        raise exception 'Pre-fix runtime canonical v2 function must contain exactly one ambiguous estimation_evidence_id lookup; found %', v_anchor_count;
    end if;
    v_expected_definition := pg_catalog.replace(v_before_definition, v_anchor, v_qualified);
    if pg_catalog.pg_get_functiondef(v_target::oid) is distinct from v_expected_definition then
        raise exception 'Forward migration changed more than the exact canonical v2 idempotent replay evidence lookup';
    end if;
    if (select procedure.proacl::text from pg_catalog.pg_proc as procedure where procedure.oid = v_target::oid)
       is distinct from v_before_privileges then
        raise exception 'Forward migration changed canonical v2 RPC execute grants';
    end if;

    if exists (
        select 1
        from nutrition_v2_evidence_fix_replay.public_functions_before_fix as before_row
        left join pg_catalog.pg_proc as procedure
          on procedure.oid = before_row.function_oid
        where procedure.oid is null
           or (
               before_row.function_oid <> v_target::oid
               and before_row.definition is distinct from pg_catalog.pg_get_functiondef(procedure.oid)
           )
           or before_row.privileges is distinct from procedure.proacl::text
           or before_row.metadata is distinct from (to_jsonb(procedure) - 'prosrc')
    ) then
        raise exception 'Forward migration changed an unrelated public function or RPC grant';
    end if;

    select count(*) into v_before_function_count
    from nutrition_v2_evidence_fix_replay.public_functions_before_fix;
    select count(*) into v_public_function_count
    from pg_catalog.pg_proc as procedure
    join pg_catalog.pg_namespace as namespace
      on namespace.oid = procedure.pronamespace
    where namespace.nspname = 'public'
      and procedure.prokind in ('f', 'p');
    if v_public_function_count <> v_before_function_count then
        raise exception 'Forward migration must not add or remove public functions';
    end if;

    if exists (
        select 1
        from nutrition_v2_evidence_fix_replay.public_columns_before_fix as before_row
        full join (
            select
                relation.relname as table_name,
                attribute.attname as column_name,
                attribute.attnum as ordinal_position,
                pg_catalog.format_type(attribute.atttypid, attribute.atttypmod) as data_type,
                attribute.attnotnull as is_not_null,
                pg_catalog.pg_get_expr(default_value.adbin, default_value.adrelid) as default_expression
            from pg_catalog.pg_class as relation
            join pg_catalog.pg_namespace as namespace
              on namespace.oid = relation.relnamespace
            join pg_catalog.pg_attribute as attribute
              on attribute.attrelid = relation.oid
            left join pg_catalog.pg_attrdef as default_value
              on default_value.adrelid = relation.oid
             and default_value.adnum = attribute.attnum
            where namespace.nspname = 'public'
              and relation.relkind in ('r', 'p')
              and attribute.attnum > 0
              and not attribute.attisdropped
        ) as after_row
          on after_row.table_name = before_row.table_name
         and after_row.column_name = before_row.column_name
        where before_row.table_name is null
           or after_row.table_name is null
           or row(before_row.ordinal_position, before_row.data_type,
                  before_row.is_not_null, before_row.default_expression)
              is distinct from
              row(after_row.ordinal_position, after_row.data_type,
                  after_row.is_not_null, after_row.default_expression)
    ) then
        raise exception 'Forward migration changed public table/column shape';
    end if;

    if exists (
        select 1
        from nutrition_v2_evidence_fix_replay.public_constraints_before_fix as before_row
        full join (
            select
                relation.relname as table_name,
                constraint_row.conname as constraint_name,
                constraint_row.contype as constraint_type,
                constraint_row.convalidated as is_validated,
                pg_catalog.pg_get_constraintdef(constraint_row.oid, true) as definition
            from pg_catalog.pg_constraint as constraint_row
            join pg_catalog.pg_class as relation
              on relation.oid = constraint_row.conrelid
            join pg_catalog.pg_namespace as namespace
              on namespace.oid = relation.relnamespace
            where namespace.nspname = 'public'
        ) as after_row
          on after_row.table_name = before_row.table_name
         and after_row.constraint_name = before_row.constraint_name
        where before_row.table_name is null
           or after_row.table_name is null
           or row(before_row.constraint_type, before_row.is_validated, before_row.definition)
              is distinct from
              row(after_row.constraint_type, after_row.is_validated, after_row.definition)
    ) then
        raise exception 'Forward migration changed an existing database constraint';
    end if;

    if exists (
        select 1
        from nutrition_v2_evidence_fix_replay.public_policies_before_fix as before_row
        full join (
            select
                policy.schemaname,
                policy.tablename,
                policy.policyname,
                policy.permissive,
                policy.roles::text as roles,
                policy.cmd,
                policy.qual,
                policy.with_check
            from pg_catalog.pg_policies as policy
            where policy.schemaname = 'public'
        ) as after_row
          on after_row.schemaname = before_row.schemaname
         and after_row.tablename = before_row.tablename
         and after_row.policyname = before_row.policyname
        where before_row.policyname is null
           or after_row.policyname is null
           or row(before_row.permissive, before_row.roles, before_row.cmd,
                  before_row.qual, before_row.with_check)
              is distinct from
              row(after_row.permissive, after_row.roles, after_row.cmd,
                  after_row.qual, after_row.with_check)
    ) then
        raise exception 'Forward migration changed an existing RLS policy';
    end if;

    if exists (
        select 1
        from nutrition_v2_evidence_fix_replay.public_rls_before_fix as before_row
        full join (
            select
                relation.relname as table_name,
                relation.relrowsecurity as rls_enabled,
                relation.relforcerowsecurity as rls_forced,
                relation.relowner as table_owner,
                relation.relacl::text as privileges
            from pg_catalog.pg_class as relation
            join pg_catalog.pg_namespace as namespace
              on namespace.oid = relation.relnamespace
            where namespace.nspname = 'public'
              and relation.relkind in ('r', 'p')
        ) as after_row
          on after_row.table_name = before_row.table_name
        where before_row.table_name is null
           or after_row.table_name is null
           or row(before_row.rls_enabled, before_row.rls_forced, before_row.table_owner, before_row.privileges)
              is distinct from row(after_row.rls_enabled, after_row.rls_forced, after_row.table_owner, after_row.privileges)
    ) then
        raise exception 'Forward migration changed an existing table RLS setting';
    end if;

    for v_before_data in
        select * from nutrition_v2_evidence_fix_replay.public_data_before_fix
        order by table_name
    loop
        execute pg_catalog.format(
            'select coalesce(jsonb_agg(to_jsonb(table_row) order by to_jsonb(table_row)::text), ''[]''::jsonb) from public.%I as table_row',
            v_before_data.table_name
        ) into v_after_rows;
        if v_after_rows is distinct from v_before_data.rows then
            raise exception 'Forward migration changed existing rows in public.%', v_before_data.table_name;
        end if;
    end loop;

    drop schema nutrition_v2_evidence_fix_replay cascade;
end;
$assert_canonical_v2_evidence_fix$;

select 'PASS canonical v2 forward fix changed only the exact replay evidence lookup; function contract/metadata/grants, other RPCs, table shape, constraints, RLS, and all public rows preserved' as result;
