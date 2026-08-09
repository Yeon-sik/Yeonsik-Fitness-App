-- Keep the food kind (ingredient/menu/recipe) separate from the food category
-- and the preparation method selected by the user.
alter table public.nutrition_foods
    add column if not exists category text not null default 'other',
    add column if not exists cooking_method text not null default 'unspecified';

update public.nutrition_foods
set category = 'other'
where category is null
   or category not in (
       'meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable', 'fruit',
       'legume', 'dairy', 'nut_seed', 'processed', 'beverage', 'recipe', 'other'
   );

update public.nutrition_foods
set cooking_method = 'unspecified'
where cooking_method is null
   or cooking_method not in (
       'unspecified', 'raw', 'grilled', 'stir_fried', 'boiled', 'steamed',
       'fried', 'blanched', 'air_fried', 'baked', 'other'
   );

alter table public.nutrition_foods
    drop constraint if exists nutrition_foods_category_valid,
    drop constraint if exists nutrition_foods_cooking_method_valid;

alter table public.nutrition_foods
    add constraint nutrition_foods_category_valid check (
        category in (
            'meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable', 'fruit',
            'legume', 'dairy', 'nut_seed', 'processed', 'beverage', 'recipe', 'other'
        )
    ),
    add constraint nutrition_foods_cooking_method_valid check (
        cooking_method in (
            'unspecified', 'raw', 'grilled', 'stir_fried', 'boiled', 'steamed',
            'fried', 'blanched', 'air_fried', 'baked', 'other'
        )
    );

create index if not exists nutrition_foods_owner_category_idx
    on public.nutrition_foods (owner_id, category, cooking_method, name);

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
        new.category,
        new.basis_amount,
        new.basis_unit,
        new.prep_state,
        new.cooking_method,
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
        old.category,
        old.basis_amount,
        old.basis_unit,
        old.prep_state,
        old.cooking_method,
        old.calories_kcal,
        old.protein_grams,
        old.carbs_grams,
        old.fat_grams,
        old.sodium_mg,
        old.saturated_fat_grams,
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

comment on column public.nutrition_foods.category is
    'Food category selected by the user; the name remains free text.';
comment on column public.nutrition_foods.cooking_method is
    'Preparation method selected by the user, such as grilled, stir_fried, or boiled.';
