-- The public V1 import has a separate catalog path from its legacy delegate.
-- Qualify the mapping columns without changing its parameters or access boundary.
do $qualify_verified_nutrition_mapping$
declare
    v_oid oid;
    v_original text;
    v_repaired text;
    v_from text := E'select nutrition_food_id into v_food_id\n    from public.nutrition_verified_catalog_keys\n    where owner_id = v_user_id and catalog_key = v_catalog_key\n    for update;';
    v_to text := E'select catalog_mapping.nutrition_food_id into v_food_id\n    from public.nutrition_verified_catalog_keys as catalog_mapping\n    where catalog_mapping.owner_id = v_user_id and catalog_mapping.catalog_key = v_catalog_key\n    for update;';
begin
    select p.oid into strict v_oid
    from pg_catalog.pg_proc p
    join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prokind = 'f'
      and p.proname = 'import_verified_nutrition_v1';

    v_original := pg_catalog.pg_get_functiondef(v_oid);
    v_repaired := replace(v_original, v_from, v_to);
    if v_repaired = v_original then
        raise exception 'Catalog mapping statement was not found in public.import_verified_nutrition_v1';
    end if;
    execute v_repaired;
end
$qualify_verified_nutrition_mapping$;
