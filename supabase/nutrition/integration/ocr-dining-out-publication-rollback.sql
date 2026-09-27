-- Run only against a disposable/local Nutrition database after all migrations.
-- This transaction injects a failure at publication-event insert, then proves
-- identity, link, visibility, event, and idempotency writes all rolled back.

begin;

insert into public.nutrition_foods (
    id, owner_id, name, brand, kind, category, basis_amount, basis_unit,
    prep_state, calories_kcal, protein_grams, carbs_grams, fat_grams,
    sodium_mg, saturated_fat_grams, sugars_grams, source_type,
    source_reference, source_version, data_version, visibility
) values (
    'ocr-publication-rollback-fixture',
    '00000000-0000-4000-8000-000000000071',
    'Rollback Fixture Menu',
    'Rollback Fixture Restaurant',
    'external_menu',
    'recipe',
    1,
    'serving',
    'as_served',
    450,
    22,
    51,
    15,
    650,
    5,
    8,
    'food_image_estimate',
    jsonb_build_object(
        'schema_version', 'dining-out-identity.v1',
        'namespace', 'fitnessapp',
        'restaurant_id', null,
        'restaurant_location_id', null,
        'restaurant_menu_id', null,
        'catalog_product_id', null,
        'restaurant_name', 'Rollback Fixture Restaurant',
        'menu_name', 'Rollback Fixture Menu',
        'contract_version', 'fitness-nutrition-verified-import.v1',
        'evidence_type', 'restaurant_estimate',
        'user_verified', true,
        'pricetrace_identity', null
    )::text,
    'nutrition-projection.v3',
    2,
    'private'
);

insert into public.nutrition_verified_imports (
    id, owner_id, contract_version, idempotency_key, source_document_ref,
    evidence_type, food_name, brand, category, basis_amount, basis_unit,
    required_nutrients, optional_nutrients, provenance, user_verified,
    pricetrace_identity, catalog_key, request_payload, nutrition_food_id
) values (
    '00000000-0000-4000-8000-000000000072',
    '00000000-0000-4000-8000-000000000071',
    'fitness-nutrition-verified-import.v1',
    'rollback-fixture-import',
    'ocr-app://rollback-fixture',
    'restaurant_estimate',
    'Rollback Fixture Menu',
    'Rollback Fixture Restaurant',
    'recipe',
    1,
    'serving',
    '{"calories_kcal":450,"protein_grams":22,"carbs_grams":51,"fat_grams":15,"sodium_mg":650,"saturated_fat_grams":5,"sugars_grams":8}'::jsonb,
    '{}'::jsonb,
    '{"source_type":"food_image_estimate","estimated":true}'::jsonb,
    true,
    null,
    'rollback-fixture-menu',
    '{"contract_version":"fitness-nutrition-verified-import.v1"}'::jsonb,
    'ocr-publication-rollback-fixture'
);

insert into public.nutrition_canonical_imports (
    id, owner_id, input_contract, idempotency_key, source_document_ref,
    user_verified, required_nutrients, optional_nutrients, nutrient_provenance,
    provenance, pricetrace_identity, request_payload, projection_import_id,
    nutrition_food_id, projection_source_type
) values (
    '00000000-0000-4000-8000-000000000073',
    '00000000-0000-4000-8000-000000000071',
    'food-estimate.v1',
    'rollback-fixture-canonical',
    'ocr-app://rollback-fixture',
    true,
    '{"calories_kcal":450,"protein_grams":22,"carbs_grams":51,"fat_grams":15,"sodium_mg":650,"saturated_fat_grams":5,"sugars_grams":8}'::jsonb,
    '{}'::jsonb,
    '{}'::jsonb,
    '{"restaurant_name":"Rollback Fixture Restaurant","estimated":true}'::jsonb,
    null,
    '{"contract_version":"fitness-nutrition-canonical-import.v2","input_contract":"food-estimate.v1"}'::jsonb,
    '00000000-0000-4000-8000-000000000072',
    'ocr-publication-rollback-fixture',
    'food_image_estimate'
);

create or replace function public.ocr_publication_rollback_test_fail_event()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    raise exception 'injected OCR publication rollback failure' using errcode = 'P0001';
end;
$$;

create trigger ocr_publication_rollback_test_fail_event
before insert on public.nutrition_dining_out_publication_events
for each row execute function public.ocr_publication_rollback_test_fail_event();

select set_config('request.jwt.claim.sub', '00000000-0000-4000-8000-000000000071', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"00000000-0000-4000-8000-000000000071","role":"authenticated"}',
    true
);

do $$
declare
    v_food public.nutrition_foods%rowtype;
    v_count integer;
begin
    begin
        perform 1
        from public.publish_verified_ocr_dining_out_nutrition_v1(
            'rollback-fixture-publication',
            '00000000-0000-4000-8000-000000000073',
            'ocr-publication-rollback-fixture',
            '00000000-0000-4000-8000-000000000074',
            '00000000-0000-4000-8000-000000000075',
            '00000000-0000-4000-8000-000000000076',
            '00000000-0000-4000-8000-000000000077'
        );
        raise exception 'expected the injected publication failure';
    exception when others then
        if sqlerrm <> 'injected OCR publication rollback failure' then
            raise;
        end if;
    end;

    select food.* into v_food
    from public.nutrition_foods as food
    where food.id = 'ocr-publication-rollback-fixture';
    if v_food.visibility <> 'private' or v_food.publication_revision <> 0 or v_food.revision <> 1 then
        raise exception 'Nutrition row changed despite atomic rollback';
    end if;
    if (v_food.source_reference::jsonb ->> 'restaurant_id') is not null then
        raise exception 'PriceTrace identity attachment survived rollback';
    end if;

    select count(*) into v_count
    from public.product_nutrition_links as link
    where link.nutrition_food_id = 'ocr-publication-rollback-fixture';
    if v_count <> 0 then raise exception 'Nutrition link survived rollback'; end if;

    select count(*) into v_count
    from public.nutrition_dining_out_publication_events as event
    where event.nutrition_food_id = 'ocr-publication-rollback-fixture';
    if v_count <> 0 then raise exception 'Publication event survived rollback'; end if;

    select count(*) into v_count
    from public.nutrition_ocr_dining_out_publications as publication
    where publication.owner_id = '00000000-0000-4000-8000-000000000071'
      and publication.idempotency_key = 'rollback-fixture-publication';
    if v_count <> 0 then raise exception 'Idempotency result survived rollback'; end if;
end;
$$;

drop trigger ocr_publication_rollback_test_fail_event
    on public.nutrition_dining_out_publication_events;
drop function public.ocr_publication_rollback_test_fail_event();

select 'PASS OCR dining-out publication transaction rollback' as result;

rollback;
