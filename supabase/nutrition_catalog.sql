-- FitnessApp nutrition catalog
-- Run this migration in the new Supabase project's SQL Editor.
-- The Android app must use the project's anon key and an authenticated user for writes.

create table if not exists public.nutrition_foods (
    id text primary key,
    owner_id text,
    name text not null,
    kind text not null check (kind in ('ingredient', 'external_menu', 'recipe')),
    basis_amount numeric not null check (basis_amount > 0),
    basis_unit text not null,
    calories_kcal numeric not null default 0 check (calories_kcal >= 0),
    protein_grams numeric not null default 0 check (protein_grams >= 0),
    carbs_grams numeric not null default 0 check (carbs_grams >= 0),
    fat_grams numeric not null default 0 check (fat_grams >= 0),
    source_type text not null default 'manual',
    source_reference text,
    visibility text not null default 'private' check (visibility in ('public', 'private')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists public.nutrition_food_components (
    id text primary key,
    owner_id text,
    parent_food_id text not null references public.nutrition_foods(id) on delete cascade,
    child_food_id text not null references public.nutrition_foods(id),
    quantity numeric not null check (quantity > 0),
    unit text not null,
    order_index integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create index if not exists nutrition_foods_public_name_idx
    on public.nutrition_foods (name);
create index if not exists nutrition_foods_owner_name_idx
    on public.nutrition_foods (owner_id, name);
create index if not exists nutrition_food_components_parent_order_idx
    on public.nutrition_food_components (parent_food_id, order_index);

alter table public.nutrition_foods enable row level security;
alter table public.nutrition_food_components enable row level security;

grant select on public.nutrition_foods to anon, authenticated;
grant select on public.nutrition_food_components to anon, authenticated;
grant insert, update, delete on public.nutrition_foods to authenticated;
grant insert, update, delete on public.nutrition_food_components to authenticated;

drop policy if exists nutrition_foods_select on public.nutrition_foods;
create policy nutrition_foods_select
    on public.nutrition_foods
    for select
    using (
        deleted_at is null
        and (visibility = 'public' or owner_id = auth.uid()::text)
    );

drop policy if exists nutrition_foods_insert on public.nutrition_foods;
create policy nutrition_foods_insert
    on public.nutrition_foods
    for insert
    to authenticated
    with check (
        owner_id = auth.uid()::text
        and visibility = 'private'
    );

drop policy if exists nutrition_foods_update on public.nutrition_foods;
create policy nutrition_foods_update
    on public.nutrition_foods
    for update
    to authenticated
    using (owner_id = auth.uid()::text)
    with check (
        owner_id = auth.uid()::text
        and visibility = 'private'
    );

drop policy if exists nutrition_foods_delete on public.nutrition_foods;
create policy nutrition_foods_delete
    on public.nutrition_foods
    for delete
    to authenticated
    using (owner_id = auth.uid()::text);

drop policy if exists nutrition_food_components_select on public.nutrition_food_components;
create policy nutrition_food_components_select
    on public.nutrition_food_components
    for select
    using (
        deleted_at is null
        and (
            owner_id = auth.uid()::text
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

drop policy if exists nutrition_food_components_insert on public.nutrition_food_components;
create policy nutrition_food_components_insert
    on public.nutrition_food_components
    for insert
    to authenticated
    with check (owner_id = auth.uid()::text);

drop policy if exists nutrition_food_components_update on public.nutrition_food_components;
create policy nutrition_food_components_update
    on public.nutrition_food_components
    for update
    to authenticated
    using (owner_id = auth.uid()::text)
    with check (owner_id = auth.uid()::text);

drop policy if exists nutrition_food_components_delete on public.nutrition_food_components;
create policy nutrition_food_components_delete
    on public.nutrition_food_components
    for delete
    to authenticated
    using (owner_id = auth.uid()::text);
