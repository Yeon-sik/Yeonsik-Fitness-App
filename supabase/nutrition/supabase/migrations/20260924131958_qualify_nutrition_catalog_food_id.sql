-- Qualify catalog mapping columns that collide with RETURNS TABLE output variables.
-- Recreate deployed definitions so the existing import behavior and grants stay intact.
do $qualify_nutrition_catalog_food_id$
declare
    v_name text;
    v_count integer;
    v_oid oid;
    v_original text;
    v_repaired text;
    v_from text := E'select nutrition_food_id into v_food_id\n    from public.nutrition_verified_catalog_keys\n    where owner_id = v_user_id and catalog_key = v_catalog_key\n    for update;';
    v_repaired_count integer := 0;
    v_to text := E'select catalog_mapping.nutrition_food_id into v_food_id\n    from public.nutrition_verified_catalog_keys as catalog_mapping\n    where catalog_mapping.owner_id = v_user_id and catalog_mapping.catalog_key = v_catalog_key\n    for update;';
begin
    foreach v_name in array array[
        'import_verified_nutrition_v1_legacy',
        'import_external_reference_nutrition_v1'
    ] loop
        select count(*), min(p.oid) into v_count, v_oid
        from pg_catalog.pg_proc p
        join pg_catalog.pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and p.prokind = 'f' and p.proname = v_name;

        -- Local and linked histories currently expose different sides of this pair.
        if v_count = 0 then
            continue;
        end if;
        if v_count <> 1 then
            raise exception 'Expected one public.% function; found %', v_name, v_count;
        end if;

        v_original := pg_catalog.pg_get_functiondef(v_oid);
        v_repaired := replace(v_original, v_from, v_to);
        if v_repaired = v_original then
            raise exception 'Catalog mapping statement was not found in public.%', v_name;
        end if;
        execute v_repaired;
        v_repaired_count := v_repaired_count + 1;
    end loop;
    if v_repaired_count = 0 then
        raise exception 'No Nutrition catalog mapping function was repaired';
    end if;
end
$qualify_nutrition_catalog_food_id$;
