
create table if not exists public.nutrition_foods (
    id text primary key,
    owner_id text,
    name text not null,
    kind text not null check (kind in ('ingredient', 'external_menu', 'recipe')),

    basis_amount numeric not null check (basis_amount > 0),
    basis_unit text not null,
    prep_state text not null default 'unspecified'
        check (prep_state in ('unspecified', 'raw', 'cooked', 'as_served', 'dried', 'frozen')),

    calories_kcal numeric not null default 0 check (calories_kcal >= 0),
    protein_grams numeric not null default 0 check (protein_grams >= 0),
    carbs_grams numeric not null default 0 check (carbs_grams >= 0),
    fat_grams numeric not null default 0 check (fat_grams >= 0),
    sodium_mg numeric check (sodium_mg >= 0),
    saturated_fat_grams numeric check (saturated_fat_grams >= 0),
    sugars_grams numeric check (sugars_grams >= 0),

    fiber_grams numeric check (fiber_grams >= 0),
    added_sugars_grams numeric check (added_sugars_grams >= 0),
    trans_fat_grams numeric check (trans_fat_grams >= 0),
    cholesterol_mg numeric check (cholesterol_mg >= 0),

    source_type text not null default 'manual',
    source_reference text,
    source_version text,
    data_version integer not null default 1 check (data_version >= 1),

    visibility text not null default 'private' check (visibility in ('public', 'private')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,

    constraint nutrition_foods_required_nutrients_present check (
        data_version < 2
        or (
            sodium_mg is not null
            and saturated_fat_grams is not null
            and sugars_grams is not null
        )
    )
);

alter table public.nutrition_foods
    add column if not exists prep_state text not null default 'unspecified',
    add column if not exists sodium_mg numeric,
    add column if not exists saturated_fat_grams numeric,
    add column if not exists sugars_grams numeric,
    add column if not exists fiber_grams numeric,
    add column if not exists added_sugars_grams numeric,
    add column if not exists trans_fat_grams numeric,
    add column if not exists cholesterol_mg numeric,
    add column if not exists source_version text,
    add column if not exists data_version integer not null default 1;

create table if not exists public.nutrition_food_nutrients (
    id text primary key,
    owner_id text,
    food_id text not null references public.nutrition_foods(id) on delete cascade,
    nutrient_code text not null check (nutrient_code in (
        'calcium', 'iron', 'magnesium', 'potassium', 'zinc',
        'phosphorus', 'copper', 'manganese', 'selenium', 'iodine',
        'vitamin_a', 'vitamin_d', 'vitamin_e', 'vitamin_k', 'vitamin_c',
        'vitamin_b1', 'vitamin_b2', 'vitamin_b3', 'vitamin_b5',
        'vitamin_b6', 'vitamin_b7', 'vitamin_b9', 'vitamin_b12'
    )),
    amount numeric check (amount >= 0),
    unit text not null check (unit in ('mg', 'ug')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (food_id, nutrient_code)
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
create index if not exists nutrition_food_nutrients_food_idx
    on public.nutrition_food_nutrients (food_id, nutrient_code);
create index if not exists nutrition_food_components_parent_order_idx
    on public.nutrition_food_components (parent_food_id, order_index);

alter table public.nutrition_foods enable row level security;
alter table public.nutrition_food_nutrients enable row level security;
alter table public.nutrition_food_components enable row level security;

grant select on public.nutrition_foods to anon, authenticated;
grant select on public.nutrition_food_nutrients to anon, authenticated;
grant select on public.nutrition_food_components to anon, authenticated;
grant insert, update, delete on public.nutrition_foods to authenticated;
grant insert, update, delete on public.nutrition_food_nutrients to authenticated;
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

drop policy if exists nutrition_food_nutrients_select on public.nutrition_food_nutrients;
create policy nutrition_food_nutrients_select
    on public.nutrition_food_nutrients
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
                    where parent.id = food_id
                      and parent.visibility = 'public'
                      and parent.deleted_at is null
                )
            )
        )
    );

drop policy if exists nutrition_food_nutrients_insert on public.nutrition_food_nutrients;
create policy nutrition_food_nutrients_insert
    on public.nutrition_food_nutrients
    for insert
    to authenticated
    with check (owner_id = auth.uid()::text);

drop policy if exists nutrition_food_nutrients_update on public.nutrition_food_nutrients;
create policy nutrition_food_nutrients_update
    on public.nutrition_food_nutrients
    for update
    to authenticated
    using (owner_id = auth.uid()::text)
    with check (owner_id = auth.uid()::text);

drop policy if exists nutrition_food_nutrients_delete on public.nutrition_food_nutrients;
create policy nutrition_food_nutrients_delete
    on public.nutrition_food_nutrients
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

do $$
declare
    leaked text;
begin
    select string_agg(table_name, ', ')
    into leaked
    from information_schema.tables
    where table_schema = 'public'
      and table_name in (
          'meal_records',
          'meal_record_items',
          'meal_record_item_nutrients',
          'weight_records',
          'workout_records',
          'workout_exercises',
          'workout_sets'
      );

    if leaked is not null then
        raise exception
            'Nutrition Catalog project must hold food data only, but found user record tables: %',
            leaked;
    end if;
end
$$;
