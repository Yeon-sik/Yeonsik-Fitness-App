-- Active shared-project init-plan optimization.
alter policy nutrition_foods_select
    on public.nutrition_foods
    using (
        deleted_at is null
        and (visibility = 'public' or owner_id = ((select auth.uid())::text))
    );

alter policy nutrition_foods_insert
    on public.nutrition_foods
    with check (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
    );

alter policy nutrition_foods_update
    on public.nutrition_foods
    using (owner_id = ((select auth.uid())::text))
    with check (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
    );

alter policy nutrition_foods_delete
    on public.nutrition_foods
    using (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_nutrients_select
    on public.nutrition_food_nutrients
    using (
        deleted_at is null
        and (
            owner_id = ((select auth.uid())::text)
            or (
                owner_id is null
                and exists (
                    select 1
                    from public.nutrition_foods parent
                    where parent.id = food_id
                      and parent.visibility = 'public'
                      and parent.deleted_at is null
                )
            )
        )
    );

alter policy nutrition_food_nutrients_insert
    on public.nutrition_food_nutrients
    with check (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_nutrients_update
    on public.nutrition_food_nutrients
    using (owner_id = ((select auth.uid())::text))
    with check (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_nutrients_delete
    on public.nutrition_food_nutrients
    using (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_components_select
    on public.nutrition_food_components
    using (
        deleted_at is null
        and (
            owner_id = ((select auth.uid())::text)
            or (
                owner_id is null
                and exists (
                    select 1
                    from public.nutrition_foods parent
                    where parent.id = parent_food_id
                      and parent.visibility = 'public'
                      and parent.deleted_at is null
                )
            )
        )
    );

alter policy nutrition_food_components_insert
    on public.nutrition_food_components
    with check (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_components_update
    on public.nutrition_food_components
    using (owner_id = ((select auth.uid())::text))
    with check (owner_id = ((select auth.uid())::text));

alter policy nutrition_food_components_delete
    on public.nutrition_food_components
    using (owner_id = ((select auth.uid())::text));
