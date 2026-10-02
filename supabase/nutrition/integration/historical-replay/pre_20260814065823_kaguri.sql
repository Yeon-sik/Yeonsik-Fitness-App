-- Disposable historical replay prerequisite only.
--
-- The exact historical migration selects one PriceTrace catalog UUID from its
-- SQL. The replay runner substitutes that selector into the placeholder below.
-- All Nutrition row data and owner identity are synthetic; this is not a
-- production snapshot and must never be added to a general seed or migration.

begin;

insert into public.nutrition_foods (
    id,
    owner_id,
    name,
    brand,
    kind,
    category,
    basis_amount,
    basis_unit,
    prep_state,
    cooking_method,
    calories_kcal,
    protein_grams,
    carbs_grams,
    fat_grams,
    sodium_mg,
    saturated_fat_grams,
    sugars_grams,
    source_type,
    source_reference,
    source_version,
    data_version,
    visibility,
    revision,
    publication_revision,
    published_at,
    published_by
) values (
    'historical-replay-kaguri-food',
    'historical-replay-owner',
    'Synthetic Kaguri prerequisite',
    'Synthetic historical replay',
    'ingredient',
    'processed',
    100,
    'g',
    'as_served',
    'unspecified',
    10,
    1,
    1,
    1,
    10,
    1,
    1,
    'manual',
    'synthetic://historical-replay/kaguri',
    'nutrition-projection.v3',
    2,
    'public',
    1,
    1,
    pg_catalog.now(),
    'historical-replay-owner'
);

insert into public.product_nutrition_links (
    owner_id,
    nutrition_food_id,
    catalog_product_id,
    status,
    source_type,
    proposal_reference,
    product_contract_version,
    revision,
    reviewed_at
) values (
    'historical-replay-owner',
    'historical-replay-kaguri-food',
    '{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}'::uuid,
    'approved',
    'manual_selection',
    null,
    'product-read.v1',
    1,
    pg_catalog.now()
);

commit;
