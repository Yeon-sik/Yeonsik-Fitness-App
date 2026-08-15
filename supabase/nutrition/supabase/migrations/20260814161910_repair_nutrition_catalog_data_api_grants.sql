-- Restore the Data API read contract for the public Nutrition catalog.
-- User-owned writes remain authenticated and protected by the existing RLS policies.

grant select on public.nutrition_foods to anon, authenticated;
grant select on public.nutrition_food_nutrients to anon, authenticated;
grant select on public.nutrition_food_components to anon, authenticated;
grant select on public.product_nutrition_links to authenticated;

drop policy if exists nutrition_foods_select on public.nutrition_foods;
create policy nutrition_foods_select
    on public.nutrition_foods
    for select
    to anon, authenticated
    using (
        deleted_at is null
        and (visibility = 'public' or owner_id = ((select auth.uid())::text))
    );

drop policy if exists nutrition_food_nutrients_select on public.nutrition_food_nutrients;
create policy nutrition_food_nutrients_select
    on public.nutrition_food_nutrients
    for select
    to anon, authenticated
    using (
        deleted_at is null
        and (
            owner_id = ((select auth.uid())::text)
            or exists (
                select 1
                from public.nutrition_foods parent
                where parent.id = food_id
                  and parent.visibility = 'public'
                  and parent.deleted_at is null
            )
        )
    );

drop policy if exists nutrition_food_components_select on public.nutrition_food_components;
create policy nutrition_food_components_select
    on public.nutrition_food_components
    for select
    to anon, authenticated
    using (
        deleted_at is null
        and (
            owner_id = ((select auth.uid())::text)
            or exists (
                select 1
                from public.nutrition_foods parent
                where parent.id = parent_food_id
                  and parent.visibility = 'public'
                  and parent.deleted_at is null
            )
        )
    );
