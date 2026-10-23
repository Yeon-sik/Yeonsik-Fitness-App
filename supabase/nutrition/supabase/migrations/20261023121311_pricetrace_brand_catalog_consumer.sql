-- PriceTrace owns Brand identity; Fitness owns nutrition and the explicit selection.
-- No data backfill and no cross-database FK. Parent snapshots refer only to the direct parent.
begin;
alter table public.nutrition_foods
    add column pricetrace_brand_id uuid,
    add column pricetrace_brand_name_snapshot text,
    add column pricetrace_parent_brand_id_snapshot uuid,
    add column pricetrace_parent_brand_name_snapshot text;

alter table public.nutrition_foods add constraint nutrition_foods_pricetrace_brand_snapshot_check check (
    (pricetrace_brand_id is null and pricetrace_brand_name_snapshot is null
        and pricetrace_parent_brand_id_snapshot is null and pricetrace_parent_brand_name_snapshot is null)
    or (pricetrace_brand_id is not null and pricetrace_brand_name_snapshot is not null
        and btrim(pricetrace_brand_name_snapshot) <> ''
        and (pricetrace_parent_brand_name_snapshot is null
            or (pricetrace_parent_brand_id_snapshot is not null and btrim(pricetrace_parent_brand_name_snapshot) <> '')))
);

create or replace function public.bump_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if row(
        new.name,
        new.brand,
        new.manufacturer_name,
        new.brand_name,
        new.sub_brand_name,
        new.product_name,
        new.pricetrace_brand_id,
        new.pricetrace_brand_name_snapshot,
        new.pricetrace_parent_brand_id_snapshot,
        new.pricetrace_parent_brand_name_snapshot,
        new.kind,
        new.basis_amount,
        new.basis_unit,
        new.prep_state,
        new.calories_kcal,
        new.protein_grams,
        new.carbs_grams,
        new.fat_grams,
        new.sodium_mg,
        new.saturated_fat_grams,
        new.sugars_grams,
        new.fiber_grams,
        new.added_sugars_grams,
        new.trans_fat_grams,
        new.cholesterol_mg,
        new.source_type,
        new.source_reference,
        new.source_version,
        new.deleted_at
    ) is distinct from row(
        old.name,
        old.brand,
        old.manufacturer_name,
        old.brand_name,
        old.sub_brand_name,
        old.product_name,
        old.pricetrace_brand_id,
        old.pricetrace_brand_name_snapshot,
        old.pricetrace_parent_brand_id_snapshot,
        old.pricetrace_parent_brand_name_snapshot,
        old.kind,
        old.basis_amount,
        old.basis_unit,
        old.prep_state,
        old.calories_kcal,
        old.protein_grams,
        old.carbs_grams,
        old.fat_grams,
        old.sodium_mg,
        old.saturated_fat_grams,
        old.sugars_grams,
        old.fiber_grams,
        old.added_sugars_grams,
        old.trans_fat_grams,
        old.cholesterol_mg,
        old.source_type,
        old.source_reference,
        old.source_version,
        old.deleted_at
    ) then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := greatest(coalesce(new.revision, old.revision), old.revision);
    end if;
    return new;
end;
$$;

-- v1/v2/v3 remain byte-for-byte unchanged. v4 wraps the current v3 projection.
-- The common contract_version value is intentionally inherited: removing the four
-- new fields from any v4 row yields exactly the corresponding v3 row.
create function public.get_nutrition_read_v4(p_query text default null)
returns table (
    contract_version text,
    nutrition_food_id text,
    brand text,
    name text,
    kind text,
    basis_amount numeric,
    basis_unit text,
    prep_state text,
    nutrition_values jsonb,
    micronutrients jsonb,
    source_type text,
    source_reference text,
    source_revision text,
    revision integer,
    catalog_product_id uuid,
    standard_product_id uuid,
    manufacturer_name text,
    brand_name text,
    sub_brand_name text,
    product_name text,
    pricetrace_brand_id uuid,
    pricetrace_brand_name_snapshot text,
    pricetrace_parent_brand_id_snapshot uuid,
    pricetrace_parent_brand_name_snapshot text
)
language sql
stable
security invoker
set search_path = ''
as $$
    select base.*, food.pricetrace_brand_id, food.pricetrace_brand_name_snapshot,
        food.pricetrace_parent_brand_id_snapshot, food.pricetrace_parent_brand_name_snapshot
    from public.get_nutrition_read_v3(p_query) as base
    join public.nutrition_foods as food on food.id = base.nutrition_food_id
    order by coalesce(base.manufacturer_name, ''), coalesce(base.brand_name, base.brand, ''),
        coalesce(base.sub_brand_name, ''), coalesce(base.product_name, base.name, ''), base.nutrition_food_id;
$$;
revoke all on function public.get_nutrition_read_v4(text) from public;
grant execute on function public.get_nutrition_read_v4(text) to anon, authenticated;
comment on function public.get_nutrition_read_v4(text) is
    'nutrition-read.v4: exact v3 common projection plus optional external PriceTrace Brand snapshots; RLS invoker.';
comment on column public.nutrition_foods.pricetrace_brand_id is
    'Explicit most-specific PriceTrace canonical Brand UUID. External reference only, never Nutrition/Product identity.';
comment on column public.nutrition_foods.pricetrace_brand_name_snapshot is
    'Display snapshot at explicit selection time; does not replace source brand strings.';
comment on column public.nutrition_foods.pricetrace_parent_brand_id_snapshot is
    'Direct parent snapshot; a non-null ID with a null name is an unknown parent label, not a root.';
comment on column public.nutrition_foods.pricetrace_parent_brand_name_snapshot is
    'Optional canonical display name snapshot for the direct parent only.';
commit;
