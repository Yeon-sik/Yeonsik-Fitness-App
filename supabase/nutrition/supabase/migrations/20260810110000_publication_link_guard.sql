-- A published Nutrition row and its approved exact-product link form one
-- publication boundary. Ordinary clients must unpublish before changing or
-- deleting that link, otherwise a row could remain public without a usable
-- unpublish path.

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

drop trigger if exists product_nutrition_links_update_guard
    on public.product_nutrition_links;
create trigger product_nutrition_links_update_guard
before update or delete on public.product_nutrition_links
for each row execute function public.guard_product_nutrition_link_update();

alter policy product_nutrition_links_insert
    on public.product_nutrition_links
    with check (
        owner_id = ((select auth.uid())::text)
        and source_type = 'manual_selection'
        and status = 'approved'
        and reviewed_at is not null
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.owner_id = ((select auth.uid())::text)
              and food.visibility = 'private'
              and food.deleted_at is null
        )
    );

alter policy product_nutrition_links_update
    on public.product_nutrition_links
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.owner_id = ((select auth.uid())::text)
              and food.visibility = 'private'
              and food.deleted_at is null
        )
    )
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.owner_id = ((select auth.uid())::text)
              and food.visibility = 'private'
              and food.deleted_at is null
        )
    );

alter policy product_nutrition_links_delete
    on public.product_nutrition_links
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods food
            where food.id = nutrition_food_id
              and food.owner_id = ((select auth.uid())::text)
              and food.visibility = 'private'
              and food.deleted_at is null
        )
    );
