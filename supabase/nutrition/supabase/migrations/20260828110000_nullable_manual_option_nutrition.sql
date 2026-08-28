-- Reusable dining-out components can be factual names without a nutrition label.
-- Keep that unknown state as NULL, while retaining the strict contract for ordinary foods.
alter table public.nutrition_foods
  alter column calories_kcal drop not null,
  alter column protein_grams drop not null,
  alter column carbs_grams drop not null,
  alter column fat_grams drop not null;

alter table public.nutrition_foods
  drop constraint if exists nutrition_foods_required_nutrients_present;

alter table public.nutrition_foods
  add constraint nutrition_foods_required_nutrients_present check (
    deleted_at is not null
    or (
      lower(source_type) = 'manual_option'
      and (
        (calories_kcal is null and protein_grams is null and carbs_grams is null and fat_grams is null)
        or
        (calories_kcal is not null and protein_grams is not null
          and carbs_grams is not null and fat_grams is not null)
      )
    )
    or (
      lower(source_type) <> 'manual_option'
      and calories_kcal is not null
      and protein_grams is not null
      and carbs_grams is not null
      and fat_grams is not null
      and (
        coalesce(data_version, 1) < 2
        or (sodium_mg is not null and saturated_fat_grams is not null and sugars_grams is not null)
      )
    )
  );
