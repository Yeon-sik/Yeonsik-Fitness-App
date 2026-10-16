-- Guard new verified/canonical import snapshots without scanning historical rows.
-- Carbohydrate and fat remain literal totals; absent optional components remain unknown.
alter table public.nutrition_verified_imports
    add constraint nutrition_verified_imports_sugars_le_carbs_check
        check (
            (required_nutrients ->> 'sugars_grams')::numeric
            <= (required_nutrients ->> 'carbs_grams')::numeric
        ) not valid,
    add constraint nutrition_verified_imports_added_sugars_le_sugars_check
        check (
            not (optional_nutrients ? 'added_sugars_grams')
            or (optional_nutrients ->> 'added_sugars_grams')::numeric
               <= (required_nutrients ->> 'sugars_grams')::numeric
        ) not valid,
    add constraint nutrition_verified_imports_saturated_fat_le_fat_check
        check (
            (required_nutrients ->> 'saturated_fat_grams')::numeric
            <= (required_nutrients ->> 'fat_grams')::numeric
        ) not valid,
    add constraint nutrition_verified_imports_trans_fat_le_fat_check
        check (
            not (optional_nutrients ? 'trans_fat_grams')
            or (optional_nutrients ->> 'trans_fat_grams')::numeric
               <= (required_nutrients ->> 'fat_grams')::numeric
        ) not valid;
