-- Forward repair: keep the deployed v4 shape and visibility; identify its own wire version.
begin;
create or replace function public.get_nutrition_read_v4(p_query text default null)
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
    select 'nutrition-read.v4'::text as contract_version,
        base.nutrition_food_id, base.brand, base.name, base.kind, base.basis_amount,
        base.basis_unit, base.prep_state, base.nutrition_values, base.micronutrients,
        base.source_type, base.source_reference, base.source_revision, base.revision,
        base.catalog_product_id, base.standard_product_id,
        base.manufacturer_name, base.brand_name, base.sub_brand_name, base.product_name,
        food.pricetrace_brand_id, food.pricetrace_brand_name_snapshot,
        food.pricetrace_parent_brand_id_snapshot, food.pricetrace_parent_brand_name_snapshot
    from public.get_nutrition_read_v3(p_query) as base
    join public.nutrition_foods as food on food.id = base.nutrition_food_id
    order by coalesce(base.manufacturer_name, ''), coalesce(base.brand_name, base.brand, ''),
        coalesce(base.sub_brand_name, ''), coalesce(base.product_name,
        base.name, ''), base.nutrition_food_id;
$$;
revoke all on function public.get_nutrition_read_v4(text) from public;
grant execute on function public.get_nutrition_read_v4(text) to anon, authenticated;
commit;
