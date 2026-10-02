-- Repair only unsupported JSON object key-count calls in existing Nutrition RPCs.
-- Recreate each deployed function from its own definition to retain its signature,
-- security mode, search_path, and unrelated behavior.
do $repair_nutrition_json_key_count$
declare
    v_name text;
    v_function_count integer;
    v_function_oid oid;
    v_original_definition text;
    v_repaired_definition text;
    v_repaired_count integer := 0;
begin
    foreach v_name in array array[
        'import_verified_nutrition_v1',
        'import_canonical_nutrition_v2',
        'import_canonical_nutrition_v3',
        'import_canonical_nutrition_v3_legacy',
        'import_meal_component_estimate_v1',
        'import_external_reference_nutrition_v1'
    ] loop
        select count(*), min(p.oid)
          into v_function_count, v_function_oid
          from pg_catalog.pg_proc p
          join pg_catalog.pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public'
           and p.prokind = 'f'
           and p.proname = v_name;

        -- This RPC is present only on installations with the external-reference migration.
        if v_function_count = 0 and v_name = 'import_external_reference_nutrition_v1' then
            continue;
        end if;
        if v_function_count <> 1 then
            raise exception 'Expected one public.% function; found %', v_name, v_function_count;
        end if;

        v_original_definition := pg_catalog.pg_get_functiondef(v_function_oid);
        v_repaired_definition := replace(
            v_original_definition,
            'jsonb_object_length(v_required)',
            '(select count(*) from pg_catalog.jsonb_object_keys(v_required))'
        );
        v_repaired_definition := replace(
            v_repaired_definition,
            'jsonb_object_length(v_nutrient_provenance)',
            '(select count(*) from pg_catalog.jsonb_object_keys(v_nutrient_provenance))'
        );

        if v_repaired_definition = v_original_definition then
            continue;
        end if;
        if position('jsonb_object_length(' in v_repaired_definition) > 0 then
            raise exception 'Unsupported JSON key-count call remains in public.%', v_name;
        end if;

        execute v_repaired_definition;
        v_repaired_count := v_repaired_count + 1;
    end loop;

    if v_repaired_count < 4 then
        raise exception 'Expected to repair at least four Nutrition functions; repaired %',
            v_repaired_count;
    end if;
    if exists (
        select 1
          from pg_catalog.pg_proc p
          join pg_catalog.pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public'
           and p.prokind = 'f'
           and (p.prosrc like '%jsonb_object_length(%'
                or p.prosrc like '%json_object_length(%')
    ) then
        raise exception 'An unsupported JSON object-length call remains in public functions';
    end if;
end
$repair_nutrition_json_key_count$;
