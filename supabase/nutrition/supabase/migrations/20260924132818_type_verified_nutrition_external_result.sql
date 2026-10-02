-- The external-reference V1 branch returns a UUID estimation_evidence_id.
-- An untyped NULL in RETURN QUERY resolves as text and fails the declared result type.
do $type_verified_nutrition_external_result$
declare
    v_oid oid;
    v_original text;
    v_repaired text;
begin
    select p.oid into strict v_oid
    from pg_catalog.pg_proc p
    join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prokind = 'f'
      and p.proname = 'import_verified_nutrition_v1';

    v_original := pg_catalog.pg_get_functiondef(v_oid);
    v_repaired := replace(
        v_original,
        'v_catalog_key, v_catalog_product_id, null, v_link_created;',
        'v_catalog_key, v_catalog_product_id, null::uuid, v_link_created;'
    );
    if v_repaired = v_original then
        raise exception 'Untyped external-reference result was not found in public.import_verified_nutrition_v1';
    end if;
    execute v_repaired;
end
$type_verified_nutrition_external_result$;
