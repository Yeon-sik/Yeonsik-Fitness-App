-- Disposable replay only. These synthetic rows deliberately violate all four future guards.
create schema nutrition_relation_guard_replay;

create table nutrition_relation_guard_replay.public_functions_before as
select procedure.oid as function_oid, procedure.proname as function_name,
       pg_catalog.pg_get_functiondef(procedure.oid) as definition,
       procedure.proacl::text as privileges
from pg_catalog.pg_proc as procedure
join pg_catalog.pg_namespace as namespace on namespace.oid = procedure.pronamespace
where namespace.nspname = 'public' and procedure.prokind in ('f', 'p');

create table nutrition_relation_guard_replay.constraints_before as
select constraint_row.oid as constraint_oid,
       pg_catalog.pg_get_constraintdef(constraint_row.oid, true) as definition,
       constraint_row.convalidated as is_validated
from pg_catalog.pg_constraint as constraint_row
where constraint_row.conrelid = 'public.nutrition_verified_imports'::regclass;

insert into public.nutrition_foods (
    id, owner_id, name, kind, category, basis_amount, basis_unit,
    calories_kcal, protein_grams, carbs_grams, fat_grams, sodium_mg,
    sugars_grams, saturated_fat_grams, added_sugars_grams, trans_fat_grams,
    source_type, data_version, visibility
) values (
    'relation-guard-historical-food', '00000000-0000-4000-8000-000000000043',
    'Synthetic historical relation fixture', 'external_menu', 'processed', 100, 'g',
    600, 20, 43, 28, 100, 50, 30, 51, 30, 'manual', 2, 'private'
);

insert into public.nutrition_verified_imports (
    id, owner_id, contract_version, idempotency_key, source_document_ref, evidence_type,
    food_name, category, basis_amount, basis_unit, required_nutrients, optional_nutrients,
    provenance, user_verified, catalog_key, request_payload, nutrition_food_id
) values (
    '00000000-0000-4000-8000-000000000044', '00000000-0000-4000-8000-000000000043',
    'fitness-nutrition-verified-import.v1', 'relation-guard-historical-import',
    'integration://nutrition-relations/historical', 'product_label',
    'Synthetic historical relation fixture', 'processed', 100, 'g',
    '{"calories_kcal":600,"protein_grams":20,"carbs_grams":43,"fat_grams":28,"sodium_mg":100,"sugars_grams":50,"saturated_fat_grams":30}',
    '{"added_sugars_grams":51,"trans_fat_grams":30}', '{}', true,
    'relation-guard-historical-catalog', '{}', 'relation-guard-historical-food'
);

create table nutrition_relation_guard_replay.historical_import_before as
select id, to_jsonb(verified_import) as original_row
from public.nutrition_verified_imports as verified_import
where id = '00000000-0000-4000-8000-000000000044';

select 'PASS pre-guard RPC definitions, grants, constraints, and synthetic historical row snapshot' as result;
