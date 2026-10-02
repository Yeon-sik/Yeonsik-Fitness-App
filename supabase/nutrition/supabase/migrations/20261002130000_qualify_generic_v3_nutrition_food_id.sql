-- Fix the generic nutrition-label.v1 / food-estimate.v1 projection path without
-- editing any historical migration or changing the legacy RPC contract.
-- The runtime replay must contain exactly this one ambiguous approved-link lookup.
do $qualify_generic_v3_nutrition_food_id$
declare
    v_target regprocedure;
    v_original text;
    v_repaired text;
    v_after text;
    v_anchor text := E'select * into v_existing_link\n        from public.product_nutrition_links\n        where owner_id = v_user_id\n          and nutrition_food_id = v_food_id\n          and status = ''approved''\n          and deleted_at is null\n        order by created_at desc\n        limit 1\n        for update;';
    v_qualified text := E'select * into v_existing_link\n        from public.product_nutrition_links as existing_link\n        where existing_link.owner_id = v_user_id\n          and existing_link.nutrition_food_id = v_food_id\n          and existing_link.status = ''approved''\n          and existing_link.deleted_at is null\n        order by existing_link.created_at desc\n        limit 1\n        for update;';
    v_anchor_count integer;
begin
    v_target := pg_catalog.to_regprocedure(
        'public.import_verified_nutrition_v1_legacy(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,boolean,jsonb,jsonb)'
    );
    if v_target is null then
        raise exception 'Expected exact public.import_verified_nutrition_v1_legacy signature was not found';
    end if;

    v_original := pg_catalog.pg_get_functiondef(v_target::oid);
    v_anchor_count := (
        pg_catalog.length(v_original)
        - pg_catalog.length(pg_catalog.replace(v_original, v_anchor, ''))
    ) / pg_catalog.length(v_anchor);
    if v_anchor_count <> 1 then
        raise exception 'Expected exactly one ambiguous approved-link lookup in public.import_verified_nutrition_v1_legacy; found %',
            v_anchor_count;
    end if;

    v_repaired := pg_catalog.replace(v_original, v_anchor, v_qualified);
    if v_repaired = v_original then
        raise exception 'The exact generic v3 approved-link lookup was not changed';
    end if;

    execute v_repaired;

    v_after := pg_catalog.pg_get_functiondef(v_target::oid);
    if pg_catalog.strpos(v_after, v_anchor) <> 0
       or pg_catalog.strpos(v_after, v_qualified) = 0 then
        raise exception 'Qualified generic v3 approved-link lookup did not persist';
    end if;
end;
$qualify_generic_v3_nutrition_food_id$;
