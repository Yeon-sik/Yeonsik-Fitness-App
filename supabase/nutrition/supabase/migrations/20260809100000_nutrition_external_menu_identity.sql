-- Fitness owns the nutrition identity. PriceTrace remains a read-only source for
-- product, seller, price, and observation metadata.
alter table public.nutrition_foods
    add column if not exists brand text;

alter table public.product_nutrition_links
    add column if not exists standard_product_id uuid;

create index if not exists nutrition_foods_owner_brand_name_idx
    on public.nutrition_foods (owner_id, brand, name);

create index if not exists product_nutrition_links_standard_idx
    on public.product_nutrition_links (standard_product_id)
    where deleted_at is null;

create or replace function public.bump_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if row(
        new.name,
        new.brand,
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

create or replace function public.guard_product_nutrition_link_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new.owner_id is distinct from old.owner_id
       or new.nutrition_food_id is distinct from old.nutrition_food_id
       or new.catalog_product_id is distinct from old.catalog_product_id
       or new.standard_product_id is distinct from old.standard_product_id
       or new.source_type is distinct from old.source_type
       or new.proposal_reference is distinct from old.proposal_reference
       or new.product_contract_version is distinct from old.product_contract_version
    then
        raise exception 'Product nutrition link identity and provenance are immutable.'
            using errcode = '23514';
    end if;

    if row(new.status, new.reviewed_at, new.deleted_at)
       is distinct from row(old.status, old.reviewed_at, old.deleted_at)
    then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := old.revision;
    end if;
    return new;
end;
$$;

-- v1 stays compatible for existing consumers. New clients can request the
-- brand-aware v2 projection without changing the existing return signature.
drop function if exists public.get_nutrition_read_v2(text);
create function public.get_nutrition_read_v2(p_query text default null)
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
    standard_product_id uuid
)
language sql
stable
security invoker
set search_path = ''
as $$
    select
        'nutrition-read.v2'::text,
        food.id,
        food.brand,
        food.name,
        food.kind,
        food.basis_amount,
        food.basis_unit,
        food.prep_state,
        jsonb_build_object(
            'calories_kcal', food.calories_kcal,
            'protein_grams', food.protein_grams,
            'carbs_grams', food.carbs_grams,
            'fat_grams', food.fat_grams,
            'sodium_mg', food.sodium_mg,
            'saturated_fat_grams', food.saturated_fat_grams,
            'sugars_grams', food.sugars_grams,
            'fiber_grams', food.fiber_grams,
            'added_sugars_grams', food.added_sugars_grams,
            'trans_fat_grams', food.trans_fat_grams,
            'cholesterol_mg', food.cholesterol_mg
        ),
        coalesce(nutrients.values, '{}'::jsonb),
        food.source_type,
        food.source_reference,
        food.source_version,
        food.revision,
        approved.catalog_product_id,
        approved.standard_product_id
    from public.nutrition_foods as food
    left join lateral (
        select jsonb_object_agg(
            nutrient.nutrient_code,
            jsonb_build_object('amount', nutrient.amount, 'unit', nutrient.unit)
        ) as values
        from public.nutrition_food_nutrients as nutrient
        where nutrient.food_id = food.id
          and nutrient.deleted_at is null
          and nutrient.amount is not null
    ) as nutrients on true
    left join lateral (
        select link.catalog_product_id, link.standard_product_id
        from public.product_nutrition_links as link
        where link.nutrition_food_id = food.id
          and link.status = 'approved'
          and link.deleted_at is null
        order by link.reviewed_at desc, link.created_at desc
        limit 1
    ) as approved on true
    where food.deleted_at is null
      and (
          nullif(btrim(p_query), '') is null
          or coalesce(food.brand, '') ilike '%' || btrim(p_query) || '%'
          or food.name ilike '%' || btrim(p_query) || '%'
      )
    order by coalesce(food.brand, ''), food.name, food.id;
$$;

comment on column public.nutrition_foods.brand is
    'Fitness-owned brand label used with name for human search and display.';
comment on column public.product_nutrition_links.standard_product_id is
    'Stable PriceTrace standard product identity; catalog_product_id remains the exact offer/catalog row.';
comment on function public.get_nutrition_read_v2(text) is
    'nutrition-read.v2: brand-aware nutrition values and both PriceTrace identity levels.';

revoke all on function public.get_nutrition_read_v2(text) from public;
grant execute on function public.get_nutrition_read_v2(text) to anon, authenticated;
