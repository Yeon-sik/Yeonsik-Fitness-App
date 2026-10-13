do $assert_nutrition_relation_guards$
declare
    v_constraint_names text[] := array[
        'nutrition_verified_imports_sugars_le_carbs_check',
        'nutrition_verified_imports_added_sugars_le_sugars_check',
        'nutrition_verified_imports_saturated_fat_le_fat_check',
        'nutrition_verified_imports_trans_fat_le_fat_check'
    ];
    v_signature text;
begin
    if (select count(*) from pg_catalog.pg_constraint as guard
        where guard.conrelid = 'public.nutrition_verified_imports'::regclass
          and guard.conname = any(v_constraint_names)
          and guard.contype = 'c' and not guard.convalidated) <> 4 then
        raise exception 'Expected four NOT VALID CHECK guards on public.nutrition_verified_imports';
    end if;
    if exists (
        select 1 from nutrition_relation_guard_replay.constraints_before as before_row
        left join pg_catalog.pg_constraint as current_row on current_row.oid = before_row.constraint_oid
        where current_row.oid is null
           or pg_catalog.pg_get_constraintdef(current_row.oid, true) is distinct from before_row.definition
           or current_row.convalidated is distinct from before_row.is_validated
    ) or (select count(*) from pg_catalog.pg_constraint
          where conrelid = 'public.nutrition_verified_imports'::regclass)
         <> (select count(*) + 4 from nutrition_relation_guard_replay.constraints_before) then
        raise exception 'Existing verified-import constraints changed or unexpected constraints were added';
    end if;
    foreach v_signature in array array[
        'public.import_verified_nutrition_v1(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,boolean,jsonb,jsonb)',
        'public.import_canonical_nutrition_v2(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb)',
        'public.import_canonical_nutrition_v3(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb,text,text,text,text)',
        'public.import_external_reference_nutrition_v1(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb,text,text,text,text)'
    ] loop
        if pg_catalog.to_regprocedure(v_signature) is null then
            raise exception 'Existing RPC signature is missing: %', v_signature;
        end if;
    end loop;
    if exists (
        select 1 from nutrition_relation_guard_replay.public_functions_before as before_row
        left join pg_catalog.pg_proc as current_row on current_row.oid = before_row.function_oid
        where current_row.oid is null
           or pg_catalog.pg_get_functiondef(current_row.oid) is distinct from before_row.definition
           or current_row.proacl::text is distinct from before_row.privileges
    ) or (select count(*) from pg_catalog.pg_proc as procedure
          join pg_catalog.pg_namespace as namespace on namespace.oid = procedure.pronamespace
          where namespace.nspname = 'public' and procedure.prokind in ('f', 'p'))
         <> (select count(*) from nutrition_relation_guard_replay.public_functions_before) then
        raise exception 'Relation migration replaced, removed, or changed an existing RPC or its grants';
    end if;
    if not exists (
        select 1 from nutrition_relation_guard_replay.historical_import_before as before_row
        join public.nutrition_verified_imports as current_row on current_row.id = before_row.id
        where to_jsonb(current_row) = before_row.original_row
    ) then
        raise exception 'Historical inconsistent import was deleted or rewritten';
    end if;
    raise notice 'PASS four NOT VALID CHECK guards, unchanged RPC signatures/bodies/grants, and preserved history';
end;
$assert_nutrition_relation_guards$;

do $assert_new_updates_guarded$
declare
    v_constraint text;
begin
    begin
        update public.nutrition_verified_imports set provenance = provenance
        where id = '00000000-0000-4000-8000-000000000044';
        raise exception 'A new UPDATE of an inconsistent historical row unexpectedly succeeded';
    exception when check_violation then
        get stacked diagnostics v_constraint = constraint_name;
        if v_constraint not in (
            'nutrition_verified_imports_sugars_le_carbs_check',
            'nutrition_verified_imports_added_sugars_le_sugars_check',
            'nutrition_verified_imports_saturated_fat_le_fat_check',
            'nutrition_verified_imports_trans_fat_le_fat_check'
        ) then
            raise exception 'UPDATE failed before reaching a relation CHECK: %', v_constraint;
        end if;
    end;
    raise notice 'PASS new UPDATE enforces a relation CHECK without rewriting historical values';
end;
$assert_new_updates_guarded$;

-- Historical rows remain owner-readable; an UPDATE must obey the new guards.
begin;
set local role authenticated;
set local request.jwt.claim.sub = '00000000-0000-4000-8000-000000000043';
do $assert_historical_read$
begin
    if not exists (
        select 1 from public.nutrition_verified_imports
        where id = '00000000-0000-4000-8000-000000000044'
          and (required_nutrients ->> 'sugars_grams')::numeric = 50
          and (optional_nutrients ->> 'added_sugars_grams')::numeric = 51
    ) then
        raise exception 'Owner cannot read its pre-migration inconsistent import';
    end if;
    raise notice 'PASS historical relation-invalid import remains owner-readable';
end;
$assert_historical_read$;
rollback;
