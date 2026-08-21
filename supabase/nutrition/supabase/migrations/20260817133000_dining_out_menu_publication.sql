-- Fitness-owned restaurant menus can be published to the public Nutrition projection only
-- after an explicit owner action and an exact PriceTrace restaurant/menu identity is present.
-- This is separate from product_nutrition_links: a restaurant menu is not a packaged product.

create table if not exists public.nutrition_dining_out_publication_events (
    id uuid primary key default gen_random_uuid(),
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    owner_id text not null,
    restaurant_id uuid,
    restaurant_location_id uuid,
    restaurant_menu_id uuid,
    catalog_product_id uuid,
    action text not null check (action in ('publish', 'unpublish')),
    food_revision integer not null check (food_revision >= 1),
    publication_revision integer not null check (publication_revision >= 1),
    nutrition_snapshot jsonb not null,
    created_at timestamptz not null default now()
);

create index if not exists nutrition_dining_out_publication_events_food_idx
    on public.nutrition_dining_out_publication_events (nutrition_food_id, created_at desc);

alter table public.nutrition_dining_out_publication_events enable row level security;

drop policy if exists nutrition_dining_out_publication_events_select
    on public.nutrition_dining_out_publication_events;
create policy nutrition_dining_out_publication_events_select
    on public.nutrition_dining_out_publication_events
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

revoke insert, update, delete on public.nutrition_dining_out_publication_events
    from anon, authenticated;

create or replace function public.set_dining_out_menu_publication_v1(
    p_nutrition_food_id text,
    p_publish boolean
)
returns table (
    nutrition_food_id text,
    restaurant_id uuid,
    restaurant_location_id uuid,
    restaurant_menu_id uuid,
    catalog_product_id uuid,
    visibility text,
    publication_revision integer,
    published_at timestamptz,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_food public.nutrition_foods%rowtype;
    v_identity jsonb;
    v_restaurant_id uuid;
    v_location_id uuid;
    v_menu_id uuid;
    v_catalog_product_id uuid;
    v_now timestamptz := now();
    v_snapshot jsonb;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods food
    where food.id = p_nutrition_food_id
      and food.owner_id = v_user_id
      and food.kind = 'external_menu'
      and food.source_type = 'manual_estimate'
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002';
    end if;

    if p_publish then
        begin
            v_identity := v_food.source_reference::jsonb;
            v_restaurant_id := (v_identity ->> 'restaurant_id')::uuid;
            v_location_id := (v_identity ->> 'restaurant_location_id')::uuid;
            v_menu_id := (v_identity ->> 'restaurant_menu_id')::uuid;
            v_catalog_product_id := (v_identity ->> 'catalog_product_id')::uuid;
        exception when others then
            raise exception 'An exact PriceTrace restaurant, location, menu, and catalog product identity is required.'
                using errcode = '23514';
        end;
        if coalesce(v_identity ->> 'schema_version', v_identity ->> 'contract_version', '') <> 'dining-out-identity.v1'
           or v_restaurant_id is null
           or v_location_id is null
           or v_menu_id is null
           or v_catalog_product_id is null
           or v_food.calories_kcal is null
           or v_food.protein_grams is null
           or v_food.carbs_grams is null
           or v_food.fat_grams is null then
            raise exception 'An exact PriceTrace identity and dining-out macro profile are required for publication.'
                using errcode = '23514';
        end if;
    else
        begin
            v_identity := v_food.source_reference::jsonb;
            v_restaurant_id := nullif(v_identity ->> 'restaurant_id', '')::uuid;
            v_location_id := nullif(v_identity ->> 'restaurant_location_id', '')::uuid;
            v_menu_id := nullif(v_identity ->> 'restaurant_menu_id', '')::uuid;
            v_catalog_product_id := nullif(v_identity ->> 'catalog_product_id', '')::uuid;
        exception when others then
            v_restaurant_id := null;
            v_location_id := null;
            v_menu_id := null;
            v_catalog_product_id := null;
        end;
    end if;

    update public.nutrition_foods food
    set visibility = case when p_publish then 'public' else 'private' end,
        publication_revision = food.publication_revision + 1,
        published_at = case when p_publish then v_now else null end,
        published_by = case when p_publish then v_user_id else null end,
        updated_at = v_now
    where food.id = p_nutrition_food_id
    returning food.* into v_food;

    v_snapshot := jsonb_build_object(
        'contract_version', 'dining-out-publication.v1',
        'nutrition_food_id', v_food.id,
        'restaurant_id', v_restaurant_id,
        'restaurant_location_id', v_location_id,
        'restaurant_menu_id', v_menu_id,
        'catalog_product_id', v_catalog_product_id,
        'restaurant_name', v_food.brand,
        'menu_name', v_food.name,
        'nutrition_values', jsonb_build_object(
            'calories_kcal', v_food.calories_kcal,
            'protein_grams', v_food.protein_grams,
            'carbs_grams', v_food.carbs_grams,
            'fat_grams', v_food.fat_grams,
            'sodium_mg', v_food.sodium_mg,
            'saturated_fat_grams', v_food.saturated_fat_grams,
            'sugars_grams', v_food.sugars_grams
        )
    );

    insert into public.nutrition_dining_out_publication_events (
        nutrition_food_id,
        owner_id,
        restaurant_id,
        restaurant_location_id,
        restaurant_menu_id,
        catalog_product_id,
        action,
        food_revision,
        publication_revision,
        nutrition_snapshot
    ) values (
        v_food.id,
        v_user_id,
        v_restaurant_id,
        v_location_id,
        v_menu_id,
        v_catalog_product_id,
        case when p_publish then 'publish' else 'unpublish' end,
        v_food.revision,
        v_food.publication_revision,
        v_snapshot
    );

    return query
    select
        v_food.id,
        v_restaurant_id,
        v_location_id,
        v_menu_id,
        v_catalog_product_id,
        v_food.visibility,
        v_food.publication_revision,
        v_food.published_at,
        v_food.updated_at;
end;
$$;

revoke all on function public.set_dining_out_menu_publication_v1(text, boolean)
    from public;
grant execute on function public.set_dining_out_menu_publication_v1(text, boolean)
    to authenticated;

comment on function public.set_dining_out_menu_publication_v1(text, boolean) is
    'Explicit Fitness owner publication for an exact PriceTrace restaurant menu identity.';

create or replace function public.attach_dining_out_menu_identity_v1(
    p_nutrition_food_id text,
    p_restaurant_id uuid,
    p_restaurant_location_id uuid,
    p_restaurant_menu_id uuid,
    p_catalog_product_id uuid
)
returns table (
    nutrition_food_id text,
    source_reference text,
    updated_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_food public.nutrition_foods%rowtype;
    v_identity jsonb;
    v_updated_at timestamptz := now();
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;

    if p_restaurant_id is null
       or p_restaurant_location_id is null
       or p_restaurant_menu_id is null
       or p_catalog_product_id is null then
        raise exception 'An exact PriceTrace identity is required.' using errcode = '23514';
    end if;

    select food.*
    into v_food
    from public.nutrition_foods as food
    where food.id = p_nutrition_food_id
      and food.owner_id = v_user_id
      and food.kind = 'external_menu'
      and food.source_type = 'manual_estimate'
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002';
    end if;

    begin
        v_identity := coalesce(nullif(btrim(v_food.source_reference), '')::jsonb, '{}'::jsonb);
    exception when others then
        v_identity := '{}'::jsonb;
    end;

    v_identity := v_identity || jsonb_build_object(
        'schema_version', 'dining-out-identity.v1',
        'namespace', 'pricetrace',
        'restaurant_id', p_restaurant_id,
        'restaurant_location_id', p_restaurant_location_id,
        'restaurant_menu_id', p_restaurant_menu_id,
        'catalog_product_id', p_catalog_product_id,
        'restaurant_name', v_food.brand,
        'menu_name', v_food.name
    );

    update public.nutrition_foods as food
    set source_reference = v_identity::text,
        updated_at = v_updated_at
    where food.id = p_nutrition_food_id
    returning food.id, food.source_reference, food.updated_at
    into nutrition_food_id, source_reference, updated_at;

    return next;
end;
$$;

revoke all on function public.attach_dining_out_menu_identity_v1(text, uuid, uuid, uuid, uuid)
    from public;
grant execute on function public.attach_dining_out_menu_identity_v1(text, uuid, uuid, uuid, uuid)
    to authenticated;

comment on function public.attach_dining_out_menu_identity_v1(text, uuid, uuid, uuid, uuid) is
    'Owner-only bridge that records the exact PT IDs returned by the authenticated PT publication transaction before Nutrition visibility is changed.';
