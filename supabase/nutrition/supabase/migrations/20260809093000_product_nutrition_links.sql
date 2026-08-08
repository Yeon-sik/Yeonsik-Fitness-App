-- Nutrition entries remain valid without a PriceTrace product. This table stores
-- only an explicit user decision (or a pending PriceTrace suggestion) that links
-- a Nutrition food to a cross-project catalog_product_id.

alter table public.nutrition_foods
    add column if not exists revision integer not null default 1;

alter table public.nutrition_foods
    drop constraint if exists nutrition_foods_revision_positive;

alter table public.nutrition_foods
    add constraint nutrition_foods_revision_positive check (revision >= 1);

create or replace function public.bump_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if row(
        new.name,
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

drop trigger if exists nutrition_foods_revision_trigger on public.nutrition_foods;
create trigger nutrition_foods_revision_trigger
before update on public.nutrition_foods
for each row execute function public.bump_nutrition_food_revision();

create or replace function public.bump_parent_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    parent_id text;
begin
    if tg_table_name = 'nutrition_food_nutrients' then
        parent_id := case when tg_op = 'DELETE' then old.food_id else new.food_id end;
        if tg_op = 'UPDATE'
           and row(new.food_id, new.nutrient_code, new.amount, new.unit, new.deleted_at)
               is not distinct from
               row(old.food_id, old.nutrient_code, old.amount, old.unit, old.deleted_at)
        then
            return new;
        end if;
    elsif tg_table_name = 'nutrition_food_components' then
        parent_id := case when tg_op = 'DELETE' then old.parent_food_id else new.parent_food_id end;
        if tg_op = 'UPDATE'
           and row(
               new.parent_food_id,
               new.child_food_id,
               new.quantity,
               new.unit,
               new.order_index,
               new.deleted_at
           ) is not distinct from row(
               old.parent_food_id,
               old.child_food_id,
               old.quantity,
               old.unit,
               old.order_index,
               old.deleted_at
           )
        then
            return new;
        end if;
    end if;

    update public.nutrition_foods
    set revision = revision + 1,
        updated_at = now()
    where id = parent_id;
    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists nutrition_food_nutrients_parent_revision
    on public.nutrition_food_nutrients;
create trigger nutrition_food_nutrients_parent_revision
after insert or update or delete on public.nutrition_food_nutrients
for each row execute function public.bump_parent_nutrition_food_revision();

drop trigger if exists nutrition_food_components_parent_revision
    on public.nutrition_food_components;
create trigger nutrition_food_components_parent_revision
after insert or update or delete on public.nutrition_food_components
for each row execute function public.bump_parent_nutrition_food_revision();

create table if not exists public.product_nutrition_links (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete cascade,
    -- Cross-project identifier from PriceTrace product-read.v1. There is
    -- intentionally no FK because PriceTrace and Nutrition use separate DBs.
    catalog_product_id uuid not null,
    status text not null check (status in ('suggested', 'approved', 'rejected')),
    source_type text not null check (
        source_type in ('manual_selection', 'pricetrace_suggestion')
    ),
    proposal_reference text,
    product_contract_version text not null default 'product-read.v1'
        check (product_contract_version = 'product-read.v1'),
    revision integer not null default 1 check (revision >= 1),
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint product_nutrition_links_review_state check (
        (source_type = 'manual_selection' and status = 'approved' and reviewed_at is not null)
        or (
            source_type = 'pricetrace_suggestion'
            and status = 'suggested'
            and reviewed_at is null
        )
        or (
            source_type = 'pricetrace_suggestion'
            and status in ('approved', 'rejected')
            and reviewed_at is not null
        )
    )
);

create or replace function public.guard_product_nutrition_link_update()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
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
before update on public.product_nutrition_links
for each row execute function public.guard_product_nutrition_link_update();

create unique index if not exists product_nutrition_links_one_approved_idx
    on public.product_nutrition_links (owner_id, nutrition_food_id)
    where status = 'approved' and deleted_at is null;

create index if not exists product_nutrition_links_pending_idx
    on public.product_nutrition_links (owner_id, nutrition_food_id, created_at desc)
    where status = 'suggested' and deleted_at is null;

create index if not exists product_nutrition_links_catalog_idx
    on public.product_nutrition_links (catalog_product_id);

alter table public.product_nutrition_links enable row level security;

grant select on public.product_nutrition_links to anon, authenticated;
grant insert, update, delete on public.product_nutrition_links to authenticated;

drop policy if exists product_nutrition_links_select on public.product_nutrition_links;
create policy product_nutrition_links_select
    on public.product_nutrition_links
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists product_nutrition_links_insert on public.product_nutrition_links;
create policy product_nutrition_links_insert
    on public.product_nutrition_links
    for insert
    to authenticated
    with check (
        owner_id = ((select auth.uid())::text)
        and source_type = 'manual_selection'
        and status = 'approved'
        and reviewed_at is not null
        and exists (
            select 1
            from public.nutrition_foods as food
            where food.id = nutrition_food_id
              and food.deleted_at is null
              and (
                  food.visibility = 'public'
                  or food.owner_id = ((select auth.uid())::text)
              )
        )
    );

drop policy if exists product_nutrition_links_update on public.product_nutrition_links;
create policy product_nutrition_links_update
    on public.product_nutrition_links
    for update
    to authenticated
    using (owner_id = ((select auth.uid())::text))
    with check (
        owner_id = ((select auth.uid())::text)
        and exists (
            select 1
            from public.nutrition_foods as food
            where food.id = nutrition_food_id
              and food.deleted_at is null
              and (
                  food.visibility = 'public'
                  or food.owner_id = ((select auth.uid())::text)
              )
        )
    );

drop policy if exists product_nutrition_links_delete on public.product_nutrition_links;
create policy product_nutrition_links_delete
    on public.product_nutrition_links
    for delete
    to authenticated
    using (owner_id = ((select auth.uid())::text));

-- Stable read contract for Nutrition consumers. JSON nulls are deliberate:
-- missing nutrient values remain unknown and must never be rewritten as zero.
create or replace function public.get_nutrition_read_v1(p_query text default null)
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
security invoker
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
        approved.catalog_product_id
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
        select link.catalog_product_id
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
          or food.name ilike '%' || btrim(p_query) || '%'
      )
    order by food.name, food.id;
$$;

comment on table public.product_nutrition_links is
    'User-approved or pending cross-project links to exact PriceTrace catalog_product_id values.';
comment on function public.get_nutrition_read_v1(text) is
    'nutrition-read.v1: basis, nullable nutrient values, source metadata, revision, and optional approved PriceTrace ID.';

revoke all on function public.get_nutrition_read_v1(text) from public;
grant execute on function public.get_nutrition_read_v1(text) to anon, authenticated;
