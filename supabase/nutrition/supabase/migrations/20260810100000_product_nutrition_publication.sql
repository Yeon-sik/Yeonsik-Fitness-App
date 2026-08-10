-- User-authored Nutrition rows remain private by default. Publication is an
-- explicit, audited action and is allowed only for an active, owner-approved
-- link to an exact PriceTrace catalog product.

alter table public.nutrition_foods
    add column if not exists publication_revision integer not null default 0,
    add column if not exists published_at timestamptz,
    add column if not exists published_by text;

alter table public.nutrition_foods
    drop constraint if exists nutrition_foods_publication_revision_nonnegative;

alter table public.nutrition_foods
    add constraint nutrition_foods_publication_revision_nonnegative
    check (publication_revision >= 0);

create table if not exists public.nutrition_food_publication_events (
    id uuid primary key default gen_random_uuid(),
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    product_nutrition_link_id uuid not null
        references public.product_nutrition_links(id) on delete restrict,
    owner_id text not null,
    catalog_product_id uuid not null,
    action text not null check (action in ('publish', 'unpublish')),
    food_revision integer not null check (food_revision >= 1),
    publication_revision integer not null check (publication_revision >= 1),
    nutrition_snapshot jsonb not null,
    created_at timestamptz not null default now()
);

create index if not exists nutrition_food_publication_events_food_idx
    on public.nutrition_food_publication_events
    (nutrition_food_id, created_at desc);

create index if not exists nutrition_food_publication_events_catalog_idx
    on public.nutrition_food_publication_events
    (catalog_product_id, created_at desc);

alter table public.nutrition_food_publication_events enable row level security;

grant select on public.nutrition_food_publication_events to authenticated;
revoke insert, update, delete on public.nutrition_food_publication_events
    from anon, authenticated;

drop policy if exists nutrition_food_publication_events_select
    on public.nutrition_food_publication_events;
create policy nutrition_food_publication_events_select
    on public.nutrition_food_publication_events
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

create or replace function public.reject_nutrition_publication_event_mutation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    raise exception 'Nutrition publication events are append-only.'
        using errcode = '55000';
end;
$$;

drop trigger if exists nutrition_food_publication_events_append_only
    on public.nutrition_food_publication_events;
create trigger nutrition_food_publication_events_append_only
before update or delete on public.nutrition_food_publication_events
for each row execute function public.reject_nutrition_publication_event_mutation();

-- Direct client writes can only mutate private owner rows. Public transitions
-- are performed by set_product_nutrition_publication_v1 after validation.
alter policy nutrition_foods_update
    on public.nutrition_foods
    using (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
    )
    with check (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
    );

alter policy nutrition_foods_delete
    on public.nutrition_foods
    using (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
    );

alter policy nutrition_food_nutrients_select
    on public.nutrition_food_nutrients
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

alter policy nutrition_food_nutrients_insert
    on public.nutrition_food_nutrients
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

alter policy nutrition_food_nutrients_update
    on public.nutrition_food_nutrients
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    )
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

alter policy nutrition_food_nutrients_delete
    on public.nutrition_food_nutrients
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

alter policy nutrition_food_components_select
    on public.nutrition_food_components
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

alter policy nutrition_food_components_insert
    on public.nutrition_food_components
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = parent_food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

alter policy nutrition_food_components_update
    on public.nutrition_food_components
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = parent_food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    )
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = parent_food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

alter policy nutrition_food_components_delete
    on public.nutrition_food_components
    using (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods parent
            where parent.id = parent_food_id
              and parent.owner_id = ((select auth.uid())::text)
              and parent.visibility = 'private'
              and parent.deleted_at is null
        )
    );

create or replace function public.set_product_nutrition_publication_v1(
    p_nutrition_food_id text,
    p_catalog_product_id uuid,
    p_publish boolean
)
returns table (
    nutrition_food_id text,
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
    v_link public.product_nutrition_links%rowtype;
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
      and food.deleted_at is null
    for update;

    if not found then
        raise exception 'The owner Nutrition row was not found.' using errcode = 'P0002';
    end if;

    select link.*
    into v_link
    from public.product_nutrition_links link
    where link.owner_id = v_user_id
      and link.nutrition_food_id = p_nutrition_food_id
      and link.catalog_product_id = p_catalog_product_id
      and (p_publish is false or (
          link.status = 'approved'
          and link.deleted_at is null
      ))
    order by
        case when link.status = 'approved' and link.deleted_at is null then 0 else 1 end,
        link.reviewed_at desc nulls last,
        link.created_at desc
    limit 1
    for update;

    if not found then
        raise exception 'An exact PriceTrace product link is required.' using errcode = '23514';
    end if;

    if p_publish and (
        v_food.basis_amount <= 0
        or nullif(btrim(v_food.basis_unit), '') is null
        or v_food.calories_kcal is null
        or v_food.protein_grams is null
        or v_food.carbs_grams is null
        or v_food.fat_grams is null
        or v_food.sodium_mg is null
        or v_food.saturated_fat_grams is null
        or v_food.sugars_grams is null
    ) then
        raise exception 'Basis and seven required Nutrition values are required for publication.'
            using errcode = '23514';
    end if;

    update public.nutrition_foods food
    set visibility = case when p_publish then 'public' else 'private' end,
        publication_revision = food.publication_revision + 1,
        published_at = case when p_publish then v_now else null end,
        published_by = case when p_publish then v_user_id else null end,
        updated_at = v_now
    where food.id = p_nutrition_food_id
    returning food.* into v_food;

    select jsonb_build_object(
        'contract_version', 'nutrition-read.v1',
        'nutrition_food_id', v_food.id,
        'name', v_food.name,
        'kind', v_food.kind,
        'basis_amount', v_food.basis_amount,
        'basis_unit', v_food.basis_unit,
        'prep_state', v_food.prep_state,
        'nutrition_values', jsonb_build_object(
            'calories_kcal', v_food.calories_kcal,
            'protein_grams', v_food.protein_grams,
            'carbs_grams', v_food.carbs_grams,
            'fat_grams', v_food.fat_grams,
            'sodium_mg', v_food.sodium_mg,
            'saturated_fat_grams', v_food.saturated_fat_grams,
            'sugars_grams', v_food.sugars_grams,
            'fiber_grams', v_food.fiber_grams,
            'added_sugars_grams', v_food.added_sugars_grams,
            'trans_fat_grams', v_food.trans_fat_grams,
            'cholesterol_mg', v_food.cholesterol_mg
        ),
        'source_type', v_food.source_type,
        'source_reference', v_food.source_reference,
        'source_revision', v_food.source_version,
        'revision', v_food.revision,
        'catalog_product_id', p_catalog_product_id
    ) into v_snapshot;

    insert into public.nutrition_food_publication_events (
        nutrition_food_id,
        product_nutrition_link_id,
        owner_id,
        catalog_product_id,
        action,
        food_revision,
        publication_revision,
        nutrition_snapshot
    ) values (
        v_food.id,
        v_link.id,
        v_user_id,
        p_catalog_product_id,
        case when p_publish then 'publish' else 'unpublish' end,
        v_food.revision,
        v_food.publication_revision,
        v_snapshot
    );

    return query
    select
        v_food.id,
        p_catalog_product_id,
        v_food.visibility,
        v_food.publication_revision,
        v_food.published_at,
        v_food.updated_at;
end;
$$;

revoke all on function public.set_product_nutrition_publication_v1(text, uuid, boolean)
    from public;
grant execute on function public.set_product_nutrition_publication_v1(text, uuid, boolean)
    to authenticated;

create or replace function public.get_public_product_nutrition_v1(
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
    catalog_product_id uuid
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
        link.catalog_product_id
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

comment on function public.set_product_nutrition_publication_v1(text, uuid, boolean) is
    'Explicit owner publication/unpublication for an approved exact PriceTrace product link.';
comment on function public.get_public_product_nutrition_v1(text, uuid) is
    'Public nutrition-read.v1 projection for an approved exact PriceTrace catalog product.';

revoke all on function public.get_public_product_nutrition_v1(text, uuid) from public;
grant execute on function public.get_public_product_nutrition_v1(text, uuid)
    to anon, authenticated;
