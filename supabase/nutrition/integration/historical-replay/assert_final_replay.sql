do $$
declare
    v_versions text[];
    v_publish_rpc regprocedure;
    v_dispatcher regprocedure;
    v_external_helper regprocedure;
    v_legacy_rpc regprocedure;
    v_publication_table oid;
    v_old_count integer;
    v_new_count integer;
begin
    select array_agg(m.version::text order by m.version)
    into v_versions
    from supabase_migrations.schema_migrations as m;

    if cardinality(v_versions) <> 29
       or not ('20260927120000' = any(v_versions))
       or not ('20261002120000' = any(v_versions)) then
        raise exception 'Final fresh replay expected all 27 recovered and 2 pending migrations, got %', v_versions;
    end if;

    v_publication_table := 'public.nutrition_ocr_dining_out_publications'::regclass;
    if not (select relrowsecurity from pg_catalog.pg_class where oid = v_publication_table) then
        raise exception 'OCR publication ledger RLS is not enabled';
    end if;
    if exists (
        select 1
        from pg_catalog.pg_policies
        where schemaname = 'public'
          and tablename = 'nutrition_ocr_dining_out_publications'
    ) then
        raise exception 'OCR publication ledger must have no direct client policies';
    end if;
    if has_table_privilege('anon', v_publication_table, 'select')
       or has_table_privilege('authenticated', v_publication_table, 'select')
       or has_table_privilege('anon', v_publication_table, 'insert')
       or has_table_privilege('authenticated', v_publication_table, 'insert') then
        raise exception 'OCR publication ledger has unexpected direct client grants';
    end if;

    v_publish_rpc := to_regprocedure(
        'public.publish_verified_ocr_dining_out_nutrition_v1(text,uuid,text,uuid,uuid,uuid,uuid)'
    );
    if v_publish_rpc is null
       or not has_function_privilege('authenticated', v_publish_rpc, 'execute')
       or has_function_privilege('anon', v_publish_rpc, 'execute') then
        raise exception 'OCR publication RPC execute grants are incorrect';
    end if;

    v_dispatcher := to_regprocedure(
        'public.import_canonical_nutrition_v3(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb,text,text,text,text)'
    );
    v_external_helper := to_regprocedure(
        'public.import_external_reference_nutrition_v1(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb,text,text,text,text)'
    );
    v_legacy_rpc := to_regprocedure(
        'public.import_canonical_nutrition_v3_legacy(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb,text,text,text,text)'
    );
    if v_dispatcher is null
       or v_external_helper is null
       or v_legacy_rpc is null
       or not has_function_privilege('authenticated', v_dispatcher, 'execute')
       or has_function_privilege('anon', v_dispatcher, 'execute')
       or has_function_privilege('authenticated', v_external_helper, 'execute')
       or has_function_privilege('anon', v_external_helper, 'execute')
       or has_function_privilege('authenticated', v_legacy_rpc, 'execute')
       or has_function_privilege('anon', v_legacy_rpc, 'execute') then
        raise exception 'External-reference helper/dispatcher grants are incorrect';
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_constraint as constraint_row
        join pg_catalog.pg_class as relation
          on relation.oid = constraint_row.conrelid
        join pg_catalog.pg_namespace as namespace
          on namespace.oid = relation.relnamespace
        where namespace.nspname = 'public'
          and relation.relname = 'nutrition_canonical_imports'
          and constraint_row.contype = 'c'
          and pg_catalog.pg_get_constraintdef(constraint_row.oid, true)
              like '%external-reference.v1%'
    ) then
        raise exception 'Sep20 external-reference input contract constraint is missing';
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_proc as procedure
        where procedure.oid = v_dispatcher
          and pg_catalog.pg_get_functiondef(procedure.oid)
              like '%import_external_reference_nutrition_v1%'
          and pg_catalog.pg_get_functiondef(procedure.oid)
              like '%import_canonical_nutrition_v3_legacy%'
    ) then
        raise exception 'Canonical v3 dispatcher does not preserve both import paths';
    end if;

    if exists (
        select 1
        from nutrition_replay_test.public_columns_before_external_reference as before_row
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
           or row(
               before_row.ordinal_position,
               before_row.data_type,
               before_row.is_not_null,
               before_row.default_expression
           ) is distinct from row(
               after_row.ordinal_position,
               after_row.data_type,
               after_row.is_not_null,
               after_row.default_expression
           )
    ) then
        raise exception 'External-reference migration changed the pre-existing public table/column shape';
    end if;

    if exists (
        select 1
        from nutrition_replay_test.public_constraints_before_external_reference as before_row
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
           or row(
               before_row.constraint_type,
               before_row.is_validated,
               before_row.definition
           ) is distinct from row(
               after_row.constraint_type,
               after_row.is_validated,
               after_row.definition
           )
    ) then
        raise exception 'External-reference migration changed an existing Sep20 or OCR constraint';
    end if;

    if exists (
        select 1
        from nutrition_replay_test.public_functions_before_external_reference as before_row
        left join pg_catalog.pg_proc as procedure
          on pg_catalog.format(
              '%I.%I(%s)',
              'public',
              procedure.proname,
              pg_catalog.pg_get_function_identity_arguments(procedure.oid)
          ) = before_row.identity
        where procedure.oid is null
           or (
               before_row.function_name <> 'import_canonical_nutrition_v3'
               and before_row.definition is distinct from pg_catalog.pg_get_functiondef(procedure.oid)
           )
           or (
               before_row.function_name not in (
                   'import_canonical_nutrition_v3',
                   'import_canonical_nutrition_v3_legacy'
               )
               and before_row.privileges is distinct from procedure.proacl::text
           )
    ) then
        raise exception 'External-reference migration removed or rewrote a pre-existing public function outside the dispatcher';
    end if;

    select count(*)
    into v_old_count
    from nutrition_replay_test.public_functions_before_external_reference as before_row;

    select count(*)
    into v_new_count
    from pg_catalog.pg_proc as procedure
    join pg_catalog.pg_namespace as namespace
      on namespace.oid = procedure.pronamespace
    where namespace.nspname = 'public'
      and procedure.prokind in ('f', 'p')
      and not exists (
          select 1
          from nutrition_replay_test.public_functions_before_external_reference as before_row
          where before_row.identity = pg_catalog.format(
              '%I.%I(%s)',
              namespace.nspname,
              procedure.proname,
              pg_catalog.pg_get_function_identity_arguments(procedure.oid)
          )
      );

    if v_new_count <> 1
       or not exists (
           select 1
           from pg_catalog.pg_proc as procedure
           where procedure.oid = v_external_helper
             and procedure.proname = 'import_external_reference_nutrition_v1'
       ) then
        raise exception 'External-reference migration must add only its helper function; found % new functions', v_new_count;
    end if;

    if exists (
        select 1
        from nutrition_replay_test.public_functions_before_external_reference as before_row
        join pg_catalog.pg_proc as procedure
          on pg_catalog.format(
              '%I.%I(%s)',
              'public',
              procedure.proname,
              pg_catalog.pg_get_function_identity_arguments(procedure.oid)
          ) = before_row.identity
        where before_row.function_name = 'import_canonical_nutrition_v3_legacy'
          and before_row.definition is distinct from pg_catalog.pg_get_functiondef(procedure.oid)
    ) then
        raise exception 'The Sep20 canonical v3 legacy RPC body was rewritten';
    end if;
end;
$$;

drop schema nutrition_replay_test cascade;

select 'PASS final 29-migration schema, RPC, GRANT, RLS, and Sep20 contract assertions' as result;
