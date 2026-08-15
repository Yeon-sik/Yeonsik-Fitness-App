-- Preserve the exact PriceTrace product-read.v1 child that was linked.
-- Legacy links may remain NULL until they are explicitly refreshed; every new
-- direct link and every approved suggestion must carry all four values.

alter table public.product_nutrition_links
    add column if not exists catalog_product_revision text,
    add column if not exists catalog_content_amount numeric,
    add column if not exists catalog_content_unit text,
    add column if not exists catalog_package_count integer;

alter table public.product_nutrition_links
    drop constraint if exists product_nutrition_links_pricetrace_metadata_check;

alter table public.product_nutrition_links
    add constraint product_nutrition_links_pricetrace_metadata_check
    check (
        (
            catalog_product_revision is null
            and catalog_content_amount is null
            and catalog_content_unit is null
            and catalog_package_count is null
        )
        or (
            catalog_product_revision ~ '^sha256:[0-9a-f]{64}$'
            and catalog_content_amount > 0
            and catalog_content_unit in ('g', 'ml', 'each')
            and catalog_package_count > 0
        )
    );

create or replace function public.guard_product_nutrition_link_update()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    v_food_id text := case when tg_op = 'DELETE'
        then old.nutrition_food_id
        else new.nutrition_food_id
    end;
begin
    if exists (
        select 1
        from public.nutrition_foods food
        where food.id = v_food_id
          and food.visibility = 'public'
          and food.deleted_at is null
    ) then
        raise exception 'Unpublish Nutrition before changing its product link.'
            using errcode = '55000';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;

    if new.owner_id is distinct from old.owner_id
       or new.nutrition_food_id is distinct from old.nutrition_food_id
       or new.catalog_product_id is distinct from old.catalog_product_id
       or new.source_type is distinct from old.source_type
       or new.proposal_reference is distinct from old.proposal_reference
       or new.product_contract_version is distinct from old.product_contract_version
    then
        raise exception 'Product nutrition link identity and provenance are immutable.'
            using errcode = '23514';
    end if;

    if row(
            new.status,
            new.reviewed_at,
            new.deleted_at,
            new.catalog_product_revision,
            new.catalog_content_amount,
            new.catalog_content_unit,
            new.catalog_package_count
       ) is distinct from row(
            old.status,
            old.reviewed_at,
            old.deleted_at,
            old.catalog_product_revision,
            old.catalog_content_amount,
            old.catalog_content_unit,
            old.catalog_package_count
       )
    then
        new.revision := greatest(coalesce(new.revision, 1), old.revision + 1);
    else
        new.revision := old.revision;
    end if;
    return new;
end;
$$;

drop trigger if exists product_nutrition_links_update_guard
    on public.product_nutrition_links;
create trigger product_nutrition_links_update_guard
before update or delete on public.product_nutrition_links
for each row execute function public.guard_product_nutrition_link_update();

drop function if exists public.get_public_product_nutrition_v1(text, uuid);

create function public.get_public_product_nutrition_v1(
    p_namespace text,
    p_catalog_product_id uuid
)
returns table (
    contract_version text,
    nutrition_food_id text,
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
    catalog_product_revision text,
    catalog_content_amount numeric,
    catalog_content_unit text,
    catalog_package_count integer
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        'nutrition-read.v1'::text,
        food.id,
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
        link.catalog_product_id,
        link.catalog_product_revision,
        link.catalog_content_amount,
        link.catalog_content_unit,
        link.catalog_package_count
    from public.product_nutrition_links link
    join public.nutrition_foods food
      on food.id = link.nutrition_food_id
    left join lateral (
        select jsonb_object_agg(
            nutrient.nutrient_code,
            jsonb_build_object('amount', nutrient.amount, 'unit', nutrient.unit)
        ) as values
        from public.nutrition_food_nutrients nutrient
        where nutrient.food_id = food.id
          and nutrient.deleted_at is null
          and nutrient.amount is not null
    ) nutrients on true
    where p_namespace = 'pricetrace'
      and link.catalog_product_id = p_catalog_product_id
      and link.status = 'approved'
      and link.deleted_at is null
      and food.visibility = 'public'
      and food.published_at is not null
      and food.deleted_at is null
    order by food.published_at desc, link.reviewed_at desc, link.created_at desc
    limit 1;
$$;

comment on function public.get_public_product_nutrition_v1(text, uuid) is
    'Public nutrition-read.v1 projection for an approved exact PriceTrace catalog product, including the linked catalog revision and specification.';

revoke all on function public.get_public_product_nutrition_v1(text, uuid) from public;
grant execute on function public.get_public_product_nutrition_v1(text, uuid)
    to anon, authenticated;
