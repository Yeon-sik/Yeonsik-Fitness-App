-- Fitness-owned canonical Meal ingest for verified yeonsik-ocr.v2 projections.
--
-- The Nutrition project remains the owner of Nutrition identity.  A Meal item stores the
-- exact Nutrition food id plus an immutable copy of the Nutrition values that were current
-- when the meal was recorded.  The catalog sync client intentionally does not include these
-- user-record tables; this RPC is their single authenticated write boundary.

create or replace function public.fitness_meal_normalize_unit_v1(p_unit text)
returns text
language sql
immutable
strict
set search_path = ''
as $$
    select case lower(btrim(p_unit))
        when 'g' then 'g'
        when 'gram' then 'g'
        when 'grams' then 'g'
        when 'mg' then 'mg'
        when 'milligram' then 'mg'
        when 'milligrams' then 'mg'
        when 'kg' then 'kg'
        when 'kilogram' then 'kg'
        when 'kilograms' then 'kg'
        when 'ml' then 'ml'
        when 'milliliter' then 'ml'
        when 'milliliters' then 'ml'
        when 'millilitre' then 'ml'
        when 'millilitres' then 'ml'
        when 'l' then 'l'
        when 'liter' then 'l'
        when 'liters' then 'l'
        when 'litre' then 'l'
        when 'litres' then 'l'
        when 'serving' then 'serving'
        when 'servings' then 'serving'
        when 'srv' then 'serving'
        when '개' then '개'
        when 'piece' then '개'
        when 'pieces' then '개'
        when 'unit' then '개'
        when 'units' then '개'
        when 'each' then '개'
        when 'portion' then 'portion'
        when 'portions' then 'portion'
        when 'pack' then 'pack'
        when 'packs' then 'pack'
        else lower(btrim(p_unit))
    end
$$;

create or replace function public.fitness_meal_unit_dimension_v1(p_unit text)
returns text
language sql
immutable
strict
set search_path = ''
as $$
    select case public.fitness_meal_normalize_unit_v1(p_unit)
        when 'g' then 'mass'
        when 'mg' then 'mass'
        when 'kg' then 'mass'
        when 'ml' then 'volume'
        when 'l' then 'volume'
        when 'serving' then 'count'
        when '개' then 'count'
        when 'portion' then 'count'
        when 'pack' then 'count'
        else null
    end
$$;

create or replace function public.fitness_meal_unit_factor_v1(p_unit text)
returns numeric
language sql
immutable
strict
set search_path = ''
as $$
    select case public.fitness_meal_normalize_unit_v1(p_unit)
        when 'mg' then 0.001::numeric
        when 'kg' then 1000::numeric
        when 'l' then 1000::numeric
        else 1::numeric
    end
$$;

-- A meal component estimate is a NutritionFood, but it is not a restaurant menu identity.
-- Keep its source/audit contract separate from the existing nutrition-label.v1 and
-- food-estimate.v1 imports so those two RPC contracts remain unchanged.
create table if not exists public.nutrition_meal_component_imports (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    contract_version text not null
        check (contract_version = 'fitness-meal-component-estimate.v1'),
    idempotency_key text not null
        check (length(btrim(idempotency_key)) between 1 and 200),
    source_document_ref text not null
        check (length(btrim(source_document_ref)) between 1 and 1000),
    food_name text not null
        check (length(btrim(food_name)) between 1 and 500),
    brand text,
    category text not null
        check (length(btrim(category)) between 1 and 100),
    basis_amount numeric not null check (basis_amount > 0),
    basis_unit text not null
        check (length(btrim(basis_unit)) between 1 and 30),
    required_nutrients jsonb not null,
    optional_nutrients jsonb not null default '{}'::jsonb,
    nutrient_provenance jsonb not null,
    provenance jsonb not null default '{}'::jsonb,
    user_verified boolean not null check (user_verified),
    pricetrace_identity jsonb,
    estimation_evidence jsonb not null,
    request_payload jsonb not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (owner_id, idempotency_key)
);

create table if not exists public.nutrition_meal_component_nutrient_provenance (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    component_import_id uuid not null
        references public.nutrition_meal_component_imports(id) on delete restrict,
    nutrient_code text not null check (nutrient_code in (
        'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
        'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
    )),
    value numeric not null check (value >= 0),
    value_status text not null check (value_status in ('observed', 'estimated')),
    source_type text not null check (source_type in (
        'food_image_estimate', 'menu_reference', 'manual'
    )),
    evidence_refs jsonb not null check (jsonb_typeof(evidence_refs) = 'array'),
    confidence numeric check (confidence is null or (confidence >= 0 and confidence <= 1)),
    uncertainty_range jsonb,
    created_at timestamptz not null default now(),
    unique (component_import_id, nutrient_code)
);

create index if not exists nutrition_meal_component_imports_food_idx
    on public.nutrition_meal_component_imports (owner_id, nutrition_food_id, created_at desc);
create index if not exists nutrition_meal_component_provenance_food_idx
    on public.nutrition_meal_component_nutrient_provenance
        (owner_id, nutrition_food_id, created_at desc);

alter table public.nutrition_meal_component_imports enable row level security;
alter table public.nutrition_meal_component_nutrient_provenance enable row level security;
revoke all on public.nutrition_meal_component_imports,
    public.nutrition_meal_component_nutrient_provenance
    from public, anon, authenticated;
grant select on public.nutrition_meal_component_imports,
    public.nutrition_meal_component_nutrient_provenance to authenticated;

drop policy if exists nutrition_meal_component_imports_select
    on public.nutrition_meal_component_imports;
create policy nutrition_meal_component_imports_select
    on public.nutrition_meal_component_imports
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists nutrition_meal_component_provenance_select
    on public.nutrition_meal_component_nutrient_provenance;
create policy nutrition_meal_component_provenance_select
    on public.nutrition_meal_component_nutrient_provenance
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

create or replace function public.import_meal_component_estimate_v1(
    p_idempotency_key text,
    p_source_document_ref text,
    p_food_name text,
    p_brand text,
    p_category text,
    p_basis_amount numeric,
    p_basis_unit text,
    p_required_nutrients jsonb,
    p_nutrient_provenance jsonb,
    p_optional_nutrients jsonb default '{}'::jsonb,
    p_provenance jsonb default '{}'::jsonb,
    p_user_verified boolean default false,
    p_pricetrace_identity jsonb default null,
    p_estimation_evidence jsonb default null
)
returns table (
    component_import_id uuid,
    idempotent_replay boolean,
    nutrition_food_id text,
    input_contract text,
    source_type text,
    data_version integer,
    visibility text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_key text := btrim(coalesce(p_idempotency_key, ''));
    v_source_document_ref text := btrim(coalesce(p_source_document_ref, ''));
    v_food_name text := btrim(coalesce(p_food_name, ''));
    v_brand text := nullif(btrim(coalesce(p_brand, '')), '');
    v_category text := lower(btrim(coalesce(p_category, '')));
    v_basis_unit text := public.fitness_meal_normalize_unit_v1(p_basis_unit);
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_nutrient_provenance jsonb := coalesce(p_nutrient_provenance, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_identity jsonb := case
        when p_pricetrace_identity is null
          or p_pricetrace_identity = 'null'::jsonb then null
        else p_pricetrace_identity
    end;
    v_estimation jsonb := case
        when p_estimation_evidence is null
          or p_estimation_evidence = 'null'::jsonb then null
        else p_estimation_evidence
    end;
    v_request_payload jsonb;
    v_existing public.nutrition_meal_component_imports%rowtype;
    v_import_id uuid := gen_random_uuid();
    v_food_id text := gen_random_uuid()::text;
    v_key_name text;
    v_item jsonb;
    v_value_status text;
    v_source_type text;
    v_has_estimated boolean := false;
    v_confidence numeric;
    v_range jsonb := '{}'::jsonb;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.'
            using errcode = '42514';
    end if;
    if length(v_key) = 0 or length(v_key) > 200
       or length(v_source_document_ref) = 0
       or length(v_source_document_ref) > 1000 then
        raise exception 'Idempotency key and source document reference are required.'
            using errcode = '22023';
    end if;
    if length(v_food_name) = 0 or length(v_category) = 0
       or p_basis_amount is null or p_basis_amount <= 0
       or public.fitness_meal_unit_dimension_v1(v_basis_unit) is null then
        raise exception 'Food name, category, and a supported positive Nutrition basis are required.'
            using errcode = '22023';
    end if;
    if v_category not in ('meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable', 'fruit',
                          'legume', 'dairy', 'nut_seed', 'processed', 'beverage', 'recipe', 'other') then
        raise exception 'category is not supported by the Nutrition catalog.' using errcode = '22023';
    end if;
    if jsonb_typeof(v_required) <> 'object'
       or jsonb_typeof(v_optional) <> 'object'
       or jsonb_typeof(v_nutrient_provenance) <> 'object'
       or jsonb_typeof(v_provenance) <> 'object' then
        raise exception 'Nutrients and provenance must be objects.' using errcode = '22023';
    end if;
    if jsonb_object_length(v_required) <> 7
       or exists (
           select 1 from jsonb_object_keys(v_required) as required_key(key_name)
           where key_name not in (
               'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
               'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
           )
       )
       or jsonb_object_length(v_nutrient_provenance) <> 7
       or exists (
           select 1 from jsonb_object_keys(v_nutrient_provenance) as provenance_key(key_name)
           where key_name not in (
               'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
               'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
           )
       ) then
        raise exception 'Meal component estimates require exactly the seven required nutrient keys.'
            using errcode = '23514';
    end if;
    if exists (
        select 1 from jsonb_object_keys(v_optional) as optional_key(key_name)
        where key_name not in ('fiber_grams', 'added_sugars_grams', 'trans_fat_grams', 'cholesterol_mg')
    ) then
        raise exception 'Unknown optional Nutrition values are not accepted.' using errcode = '22023';
    end if;
    foreach v_key_name in array ARRAY[
        'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
        'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
    ] loop
        v_item := v_nutrient_provenance -> v_key_name;
        if jsonb_typeof(v_required -> v_key_name) <> 'number'
           or jsonb_typeof(v_item) <> 'object'
           or jsonb_typeof(v_item -> 'value') <> 'number'
           or (v_item ->> 'value')::numeric <> (v_required ->> v_key_name)::numeric
           or (v_item ->> 'value')::numeric < 0
           or jsonb_typeof(v_item -> 'evidence_refs') <> 'array'
           or jsonb_array_length(v_item -> 'evidence_refs') = 0 then
            raise exception 'Every meal component nutrient needs a matching value and evidence.'
                using errcode = '23514', detail = v_key_name;
        end if;
        v_value_status := lower(btrim(coalesce(v_item ->> 'value_status', '')));
        v_source_type := lower(btrim(coalesce(v_item ->> 'source_type', '')));
        if v_value_status not in ('observed', 'estimated')
           or v_source_type not in ('food_image_estimate', 'menu_reference', 'manual') then
            raise exception 'Meal component nutrient provenance is invalid.'
                using errcode = '23514', detail = v_key_name;
        end if;
        v_has_estimated := v_has_estimated or v_value_status = 'estimated';
    end loop;
    if not v_has_estimated or coalesce(v_provenance ->> 'estimated', 'false') <> 'true' then
        raise exception 'meal_component_estimate must be explicitly estimated.' using errcode = '23514';
    end if;
    if v_estimation is null
       or jsonb_typeof(v_estimation) <> 'object'
       or jsonb_typeof(v_estimation -> 'confidence') <> 'number' then
        raise exception 'Meal component estimates require numeric confidence evidence.'
            using errcode = '23514';
    end if;
    v_confidence := (v_estimation ->> 'confidence')::numeric;
    if v_confidence < 0 or v_confidence > 1 then
        raise exception 'Estimation confidence must be between 0 and 1.' using errcode = '23514';
    end if;
    if v_estimation ? 'range' then
        if jsonb_typeof(v_estimation -> 'range') <> 'object' then
            raise exception 'Estimation range must be an object.' using errcode = '22023';
        end if;
        v_range := v_estimation -> 'range';
    end if;
    if v_identity is not null then
        if jsonb_typeof(v_identity) <> 'object'
           or (v_identity ? 'namespace' and lower(coalesce(v_identity ->> 'namespace', '')) <> 'pricetrace')
           or nullif(v_identity ->> 'restaurant_menu_id', '') is not null
           or v_identity::text ~* 'https?://' then
            raise exception 'Meal component identity may keep exact non-menu IDs, but restaurant_menu_id must be null.'
                using errcode = '23514';
        end if;
    end if;

    v_request_payload := jsonb_build_object(
        'contract_version', 'fitness-meal-component-estimate.v1',
        'idempotency_key', v_key,
        'source_document_ref', v_source_document_ref,
        'food_name', v_food_name,
        'brand', v_brand,
        'category', v_category,
        'basis_amount', p_basis_amount,
        'basis_unit', v_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'nutrient_provenance', v_nutrient_provenance,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', coalesce(v_identity, 'null'::jsonb),
        'estimation_evidence', v_estimation
    );
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':meal-component:' || v_key, 0)
    );
    select * into v_existing
    from public.nutrition_meal_component_imports
    where owner_id = v_user_id and idempotency_key = v_key
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different meal component payload.'
                using errcode = '23505';
        end if;
        return query select
            v_existing.id, true, v_existing.nutrition_food_id,
            'meal-component-estimate.v1', 'meal_component_estimate', 2, 'private';
        return;
    end if;

    insert into public.nutrition_foods (
        id, owner_id, name, brand, kind, category, basis_amount, basis_unit,
        prep_state, calories_kcal, protein_grams, carbs_grams, fat_grams,
        sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams,
        added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type,
        source_reference, source_version, data_version, visibility,
        created_at, updated_at, deleted_at
    ) values (
        v_food_id, v_user_id, v_food_name, v_brand, 'external_menu', v_category,
        p_basis_amount, v_basis_unit, 'as_served',
        (v_required ->> 'calories_kcal')::numeric,
        (v_required ->> 'protein_grams')::numeric,
        (v_required ->> 'carbs_grams')::numeric,
        (v_required ->> 'fat_grams')::numeric,
        (v_required ->> 'sodium_mg')::numeric,
        (v_required ->> 'saturated_fat_grams')::numeric,
        (v_required ->> 'sugars_grams')::numeric,
        nullif(v_optional ->> 'fiber_grams', '')::numeric,
        nullif(v_optional ->> 'added_sugars_grams', '')::numeric,
        nullif(v_optional ->> 'trans_fat_grams', '')::numeric,
        nullif(v_optional ->> 'cholesterol_mg', '')::numeric,
        'meal_component_estimate',
        jsonb_build_object(
            'contract_version', 'fitness-meal-component-estimate.v1',
            'source_document_ref', v_source_document_ref,
            'provenance', v_provenance,
            'pricetrace_identity', coalesce(v_identity, 'null'::jsonb),
            'estimation_evidence', v_estimation
        )::text,
        nullif(v_provenance ->> 'source_version', ''), 2, 'private',
        now(), now(), null
    );

    insert into public.nutrition_meal_component_imports (
        id, owner_id, contract_version, idempotency_key, source_document_ref,
        food_name, brand, category, basis_amount, basis_unit, required_nutrients,
        optional_nutrients, nutrient_provenance, provenance, user_verified,
        pricetrace_identity, estimation_evidence, request_payload, nutrition_food_id
    ) values (
        v_import_id, v_user_id, 'fitness-meal-component-estimate.v1', v_key,
        v_source_document_ref, v_food_name, v_brand, v_category, p_basis_amount,
        v_basis_unit, v_required, v_optional, v_nutrient_provenance, v_provenance,
        true, v_identity, v_estimation, v_request_payload, v_food_id
    );
    insert into public.nutrition_meal_component_nutrient_provenance (
        owner_id, nutrition_food_id, component_import_id, nutrient_code, value,
        value_status, source_type, evidence_refs, confidence, uncertainty_range
    )
    select
        v_user_id, v_food_id, v_import_id, key,
        (value ->> 'value')::numeric,
        value ->> 'value_status', value ->> 'source_type', value -> 'evidence_refs',
        v_confidence, v_range -> key
    from jsonb_each(v_nutrient_provenance);

    return query select
        v_import_id, false, v_food_id, 'meal-component-estimate.v1',
        'meal_component_estimate', 2, 'private';
end;
$$;

create table if not exists public.meal_records (
    id text primary key,
    owner_id text not null,
    date date not null,
    menu text not null,
    meal_kind text not null default 'food'
        check (meal_kind in ('food', 'dining_out')),
    fulfillment_mode text,
    store_name text,
    branch_name text,
    menu_name text,
    restaurant_id text,
    restaurant_location_id text,
    restaurant_menu_id text,
    catalog_product_id text,
    eaten_at text not null,
    nutrition_calculation_contract text not null
        default 'meal-item-snapshot.v1',
    calories numeric,
    protein_grams numeric,
    carbs_grams numeric,
    fat_grams numeric,
    is_backfilled boolean not null default false,
    backfilled_at timestamptz,
    backfill_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    source_app text not null default 'ocr-app',
    scope text not null default 'fitness',
    metadata jsonb not null default '{}'::jsonb,
    contract_version integer not null default 1,
    idempotency_key text not null
        check (length(btrim(idempotency_key)) between 1 and 200),
    source_provenance jsonb not null default '{}'::jsonb,
    pricetrace_identity jsonb,
    unique (owner_id, idempotency_key)
);

create table if not exists public.meal_record_items (
    id text primary key,
    owner_id text not null,
    meal_record_id text not null references public.meal_records(id) on delete cascade,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    client_key text,
    food_name_snapshot text not null,
    brand_snapshot text,
    manufacturer_name_snapshot text,
    brand_name_snapshot text,
    sub_brand_name_snapshot text,
    product_name_snapshot text,
    package_amount_snapshot numeric,
    package_unit_snapshot text,
    package_count_snapshot integer,
    food_kind_snapshot text,
    consumed_amount numeric not null check (consumed_amount > 0),
    consumed_unit text not null,
    consumption_confidence numeric
        check (consumption_confidence is null or (consumption_confidence >= 0 and consumption_confidence <= 1)),
    quantity numeric not null check (quantity > 0),
    unit text not null,
    basis_amount_snapshot numeric not null check (basis_amount_snapshot > 0),
    basis_unit_snapshot text not null,
    prep_state_snapshot text,
    calories numeric,
    protein_grams numeric,
    carbs_grams numeric,
    fat_grams numeric,
    sodium_mg numeric,
    saturated_fat_grams numeric,
    sugars_grams numeric,
    fiber_grams numeric,
    added_sugars_grams numeric,
    trans_fat_grams numeric,
    cholesterol_mg numeric,
    source_type_snapshot text,
    source_reference_snapshot text,
    source_version_snapshot text,
    food_data_version_snapshot integer,
    source_provenance jsonb not null default '{}'::jsonb,
    pricetrace_identity jsonb,
    order_index integer not null check (order_index >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz
);

create table if not exists public.meal_record_item_nutrients (
    id text primary key,
    owner_id text not null,
    meal_record_id text not null references public.meal_records(id) on delete cascade,
    meal_record_item_id text not null references public.meal_record_items(id) on delete cascade,
    nutrient_code text not null,
    amount numeric not null check (amount >= 0),
    unit text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    unique (meal_record_item_id, nutrient_code)
);

create table if not exists public.meal_verified_imports (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    contract_version text not null
        check (contract_version = 'fitness-meal-verified-import.v1'),
    idempotency_key text not null
        check (length(btrim(idempotency_key)) between 1 and 200),
    request_payload jsonb not null,
    meal_record_id text not null references public.meal_records(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (owner_id, idempotency_key),
    unique (meal_record_id)
);

create index if not exists meal_records_owner_date_idx
    on public.meal_records (owner_id, date desc, created_at desc);
create index if not exists meal_records_owner_eaten_at_idx
    on public.meal_records (owner_id, eaten_at desc);
create index if not exists meal_record_items_meal_order_idx
    on public.meal_record_items (meal_record_id, order_index);
create index if not exists meal_record_items_food_idx
    on public.meal_record_items (owner_id, nutrition_food_id);
create index if not exists meal_record_item_nutrients_item_idx
    on public.meal_record_item_nutrients (meal_record_item_id, nutrient_code);

alter table public.meal_records enable row level security;
alter table public.meal_record_items enable row level security;
alter table public.meal_record_item_nutrients enable row level security;
alter table public.meal_verified_imports enable row level security;

revoke all on public.meal_records,
    public.meal_record_items,
    public.meal_record_item_nutrients,
    public.meal_verified_imports
    from public, anon, authenticated;
grant select on public.meal_records,
    public.meal_record_items,
    public.meal_record_item_nutrients,
    public.meal_verified_imports
    to authenticated;

drop policy if exists meal_records_select on public.meal_records;
create policy meal_records_select on public.meal_records
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists meal_record_items_select on public.meal_record_items;
create policy meal_record_items_select on public.meal_record_items
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists meal_record_item_nutrients_select on public.meal_record_item_nutrients;
create policy meal_record_item_nutrients_select on public.meal_record_item_nutrients
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists meal_verified_imports_select on public.meal_verified_imports;
create policy meal_verified_imports_select on public.meal_verified_imports
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

create or replace function public.import_verified_meal_v1(
    p_idempotency_key text,
    p_eaten_at text,
    p_items jsonb,
    p_source jsonb,
    p_pricetrace_identity jsonb default null
)
returns table (
    meal_import_id uuid,
    meal_record_id text,
    idempotent_replay boolean,
    eaten_at text,
    record_date date,
    item_count integer,
    nutrition_food_ids text[],
    contract_version text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_key text := btrim(coalesce(p_idempotency_key, ''));
    v_eaten_at_text text := btrim(coalesce(p_eaten_at, ''));
    v_eaten_at timestamptz;
    v_local_eaten_at timestamp;
    v_offset text;
    v_offset_minutes integer;
    v_record_date date;
    v_today date;
    v_source jsonb := coalesce(p_source, '{}'::jsonb);
    v_identity jsonb := case
        when p_pricetrace_identity is null
          or p_pricetrace_identity = 'null'::jsonb then null
        else p_pricetrace_identity
    end;
    v_request_payload jsonb;
    v_existing public.meal_verified_imports%rowtype;
    v_existing_meal public.meal_records%rowtype;
    v_meal_import_id uuid := gen_random_uuid();
    v_meal_id text := gen_random_uuid()::text;
    v_food public.nutrition_foods%rowtype;
    v_item jsonb;
    v_item_source jsonb;
    v_item_identity jsonb;
    v_item_id text;
    v_consumption_confidence numeric;
    v_food_id text;
    v_client_key text;
    v_consumed_amount numeric;
    v_consumed_unit text;
    v_basis_unit text;
    v_quantity numeric;
    v_scale numeric;
    v_dimension text;
    v_basis_dimension text;
    v_item_count integer;
    v_index integer;
    v_total_calories numeric := 0;
    v_total_protein numeric := 0;
    v_total_carbs numeric := 0;
    v_total_fat numeric := 0;
    v_has_calories boolean := true;
    v_has_protein boolean := true;
    v_has_carbs boolean := true;
    v_has_fat boolean := true;
    v_meal_kind text;
    v_source_app text;
    v_menu text;
    v_namespace text;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if length(v_key) = 0 or length(v_key) > 200 then
        raise exception 'An idempotency key between 1 and 200 characters is required.'
            using errcode = '22023';
    end if;
    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'At least one verified Nutrition item is required.' using errcode = '22023';
    end if;
    if jsonb_typeof(v_source) <> 'object' or v_source = '{}'::jsonb then
        raise exception 'Meal source/provenance must be a non-empty object.' using errcode = '22023';
    end if;

    -- Keep the original offset text and reject local/naive timestamps.  The date is derived
    -- in that same supplied offset, matching MealEntryPolicy's selected record date rather
    -- than the database session timezone.
    if v_eaten_at_text !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}(:[0-9]{2}(\.[0-9]+)?)?(Z|[+-][0-9]{2}:[0-9]{2})$' then
        raise exception 'eaten_at must be an ISO timestamp with an explicit offset.'
            using errcode = '22023';
    end if;
    v_offset := case
        when right(v_eaten_at_text, 1) = 'Z' then '+00:00'
        else substring(v_eaten_at_text from '([+-][0-9]{2}:[0-9]{2})$')
    end;
    if v_offset is null then
        raise exception 'eaten_at must be an ISO timestamp with an explicit offset.'
            using errcode = '22023';
    end if;
    begin
        v_eaten_at := v_eaten_at_text::timestamptz;
        v_local_eaten_at := case
            when right(v_eaten_at_text, 1) = 'Z'
                then left(v_eaten_at_text, length(v_eaten_at_text) - 1)::timestamp
            else left(v_eaten_at_text, length(v_eaten_at_text) - 6)::timestamp
        end;
    exception when others then
        raise exception 'eaten_at must be a valid ISO offset timestamp.' using errcode = '22023';
    end;
    v_offset_minutes := (
        substring(v_offset from 2 for 2)::integer * 60
        + substring(v_offset from 5 for 2)::integer
    ) * case when left(v_offset, 1) = '-' then -1 else 1 end;
    v_record_date := v_local_eaten_at::date;
    v_today := (now() + pg_catalog.make_interval(mins => v_offset_minutes))::date;
    if v_record_date > v_today then
        raise exception 'Future meal dates are not allowed.' using errcode = '22023';
    end if;

    v_meal_kind := lower(btrim(coalesce(v_source ->> 'meal_kind', 'food')));
    if v_meal_kind not in ('food', 'dining_out') then
        raise exception 'source.meal_kind must be food or dining_out.' using errcode = '22023';
    end if;
    v_source_app := btrim(coalesce(v_source ->> 'source_app', 'ocr-app'));
    if length(v_source_app) = 0 then
        raise exception 'source.source_app is required.' using errcode = '22023';
    end if;
    v_menu := btrim(coalesce(
        nullif(v_source ->> 'menu', ''),
        nullif(v_source ->> 'meal_name', ''),
        nullif(v_source ->> 'title', ''),
        'OCR Meal'
    ));
    if length(v_menu) = 0 then
        raise exception 'A meal menu/title is required.' using errcode = '22023';
    end if;

    if v_identity is not null then
        if jsonb_typeof(v_identity) <> 'object' then
            raise exception 'PriceTrace identity must be an object or null.' using errcode = '22023';
        end if;
        v_namespace := lower(btrim(coalesce(
            v_identity ->> 'namespace',
            v_identity ->> 'source_namespace',
            ''
        )));
        if v_namespace <> '' and v_namespace <> 'pricetrace' then
            raise exception 'PriceTrace identity namespace must be pricetrace.' using errcode = '23514';
        end if;
        if v_identity::text ~* 'https?://' then
            raise exception 'PriceTrace identity stores exact IDs, not URLs.' using errcode = '23514';
        end if;
    end if;

    v_request_payload := jsonb_build_object(
        'contract_version', 'fitness-meal-verified-import.v1',
        'idempotency_key', v_key,
        'eaten_at', v_eaten_at_text,
        'items', p_items,
        'source', v_source,
        'pricetrace_identity', coalesce(v_identity, 'null'::jsonb)
    );
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':verified-meal:' || v_key, 0)
    );

    select * into v_existing
    from public.meal_verified_imports
    where owner_id = v_user_id and idempotency_key = v_key
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different meal payload.'
                using errcode = '23505';
        end if;
        select * into v_existing_meal
        from public.meal_records
        where id = v_existing.meal_record_id and owner_id = v_user_id;
        return query select
            v_existing.id,
            v_existing.meal_record_id,
            true,
            v_existing_meal.eaten_at,
            v_existing_meal.date,
            (select count(*)::integer from public.meal_record_items item
             where item.meal_record_id = v_existing.meal_record_id
               and item.owner_id = v_user_id
               and item.deleted_at is null),
            (select coalesce(array_agg(item.nutrition_food_id order by item.order_index), '{}'::text[])
             from public.meal_record_items item
             where item.meal_record_id = v_existing.meal_record_id
               and item.owner_id = v_user_id
               and item.deleted_at is null),
            v_existing.contract_version;
        return;
    end if;

    v_item_count := jsonb_array_length(p_items);
    -- First pass validates every exact food reference and calculates nullable meal totals.
    -- An unknown nutrient remains unknown for the meal summary; it is never changed to zero.
    for v_index in 0..v_item_count - 1 loop
        v_item := p_items -> v_index;
        if jsonb_typeof(v_item) <> 'object' then
            raise exception 'Each meal item must be an object.' using errcode = '22023';
        end if;
        v_food_id := nullif(btrim(coalesce(
            v_item ->> 'nutrition_food_id',
            v_item ->> 'nutrition_item_id',
            ''
        )), '');
        if v_food_id is null then
            raise exception 'Each meal item needs an exact nutrition_food_id.' using errcode = '22023';
        end if;
        if not (
            jsonb_typeof(v_item -> 'consumed_amount') = 'number'
            or jsonb_typeof(v_item -> 'amount') = 'number'
        ) then
            raise exception 'Each meal item needs a numeric consumed_amount.' using errcode = '22023';
        end if;
        v_consumed_amount := case
            when jsonb_typeof(v_item -> 'consumed_amount') = 'number'
                then (v_item ->> 'consumed_amount')::numeric
            else (v_item ->> 'amount')::numeric
        end;
        if v_consumed_amount is null or v_consumed_amount <= 0 then
            raise exception 'Meal item consumed_amount must be greater than zero.'
                using errcode = '22023';
        end if;
        v_consumed_unit := public.fitness_meal_normalize_unit_v1(coalesce(
            v_item ->> 'consumed_unit',
            v_item ->> 'unit',
            ''
        ));
        if public.fitness_meal_unit_dimension_v1(v_consumed_unit) is null then
            raise exception 'Meal item consumed_unit is not supported.' using errcode = '22023';
        end if;
        if v_item ? 'confidence' then
            if jsonb_typeof(v_item -> 'confidence') <> 'number' then
                raise exception 'Meal item confidence must be numeric.' using errcode = '22023';
            end if;
            v_consumption_confidence := (v_item ->> 'confidence')::numeric;
            if v_consumption_confidence < 0 or v_consumption_confidence > 1 then
                raise exception 'Meal item confidence must be between 0 and 1.' using errcode = '22023';
            end if;
        else
            v_consumption_confidence := null;
        end if;
        select food.* into v_food
        from public.nutrition_foods food
        where food.id = v_food_id
          and food.deleted_at is null
          and (food.visibility = 'public' or food.owner_id = v_user_id)
        for share;
        if not found then
            raise exception 'The referenced Nutrition food is not available to this owner.'
                using errcode = 'P0002';
        end if;
        v_basis_unit := public.fitness_meal_normalize_unit_v1(v_food.basis_unit);
        v_dimension := public.fitness_meal_unit_dimension_v1(v_consumed_unit);
        v_basis_dimension := public.fitness_meal_unit_dimension_v1(v_basis_unit);
        if v_dimension <> v_basis_dimension
           or (v_dimension = 'count' and v_consumed_unit <> v_basis_unit) then
            raise exception 'Meal item consumed_unit cannot be converted to its Nutrition basis unit.'
                using errcode = '22023', detail = v_consumed_unit || ' -> ' || v_basis_unit;
        end if;
        v_quantity := v_consumed_amount
            * public.fitness_meal_unit_factor_v1(v_consumed_unit)
            / public.fitness_meal_unit_factor_v1(v_basis_unit);
        v_scale := v_quantity / v_food.basis_amount;
        if v_food.calories_kcal is null then
            v_has_calories := false;
        else
            v_total_calories := v_total_calories + v_food.calories_kcal * v_scale;
        end if;
        if v_food.protein_grams is null then
            v_has_protein := false;
        else
            v_total_protein := v_total_protein + v_food.protein_grams * v_scale;
        end if;
        if v_food.carbs_grams is null then
            v_has_carbs := false;
        else
            v_total_carbs := v_total_carbs + v_food.carbs_grams * v_scale;
        end if;
        if v_food.fat_grams is null then
            v_has_fat := false;
        else
            v_total_fat := v_total_fat + v_food.fat_grams * v_scale;
        end if;
    end loop;

    insert into public.meal_records (
        id, owner_id, date, menu, meal_kind, fulfillment_mode, store_name, branch_name,
        menu_name, restaurant_id, restaurant_location_id, restaurant_menu_id,
        catalog_product_id, eaten_at, nutrition_calculation_contract, calories,
        protein_grams, carbs_grams, fat_grams, is_backfilled, backfilled_at,
        backfill_reason, source_app, scope, metadata, contract_version, idempotency_key,
        source_provenance, pricetrace_identity
    ) values (
        v_meal_id, v_user_id, v_record_date, v_menu, v_meal_kind,
        nullif(v_source ->> 'fulfillment_mode', ''),
        nullif(v_source ->> 'store_name', ''),
        nullif(v_source ->> 'branch_name', ''),
        nullif(v_source ->> 'menu_name', ''),
        nullif(coalesce(v_identity ->> 'restaurant_id', v_identity ->> 'restaurantId'), ''),
        nullif(coalesce(v_identity ->> 'restaurant_location_id', v_identity ->> 'restaurantLocationId'), ''),
        nullif(coalesce(v_identity ->> 'restaurant_menu_id', v_identity ->> 'restaurantMenuId'), ''),
        nullif(coalesce(v_identity ->> 'catalog_product_id', v_identity ->> 'catalogProductId'), ''),
        v_eaten_at_text,
        'meal-item-snapshot.v1',
        case when v_has_calories then v_total_calories else null end,
        case when v_has_protein then v_total_protein else null end,
        case when v_has_carbs then v_total_carbs else null end,
        case when v_has_fat then v_total_fat else null end,
        v_record_date < v_today,
        case when v_record_date < v_today then now() else null end,
        case when v_record_date < v_today then 'verified OCR meal import' else null end,
        v_source_app,
        'fitness',
        v_source || jsonb_build_object(
            'item_type', 'meal',
            'eaten_at', v_eaten_at_text,
            'item_count', v_item_count,
            'contract_version', 'fitness-meal-verified-import.v1'
        ),
        1,
        v_key,
        v_source,
        v_identity
    );

    insert into public.meal_verified_imports (
        id, owner_id, contract_version, idempotency_key, request_payload, meal_record_id
    ) values (
        v_meal_import_id, v_user_id, 'fitness-meal-verified-import.v1', v_key,
        v_request_payload, v_meal_id
    );

    -- Second pass copies Nutrition identity and values.  consumed_amount/unit remain the
    -- user's actual input; quantity/unit is the same amount normalized to the food basis.
    for v_index in 0..v_item_count - 1 loop
        v_item := p_items -> v_index;
        v_food_id := nullif(btrim(coalesce(
            v_item ->> 'nutrition_food_id',
            v_item ->> 'nutrition_item_id',
            ''
        )), '');
        v_consumed_amount := case
            when jsonb_typeof(v_item -> 'consumed_amount') = 'number'
                then (v_item ->> 'consumed_amount')::numeric
            else (v_item ->> 'amount')::numeric
        end;
        v_consumed_unit := public.fitness_meal_normalize_unit_v1(coalesce(
            v_item ->> 'consumed_unit',
            v_item ->> 'unit',
            ''
        ));
        select food.* into v_food
        from public.nutrition_foods food
        where food.id = v_food_id
          and food.deleted_at is null
          and (food.visibility = 'public' or food.owner_id = v_user_id)
        for share;
        v_basis_unit := public.fitness_meal_normalize_unit_v1(v_food.basis_unit);
        v_quantity := v_consumed_amount
            * public.fitness_meal_unit_factor_v1(v_consumed_unit)
            / public.fitness_meal_unit_factor_v1(v_basis_unit);
        v_scale := v_quantity / v_food.basis_amount;
        v_item_source := v_item -> 'source_provenance';
        if v_item_source is null or v_item_source = 'null'::jsonb then
            v_item_source := v_item -> 'provenance';
        end if;
        if v_item_source is null or v_item_source = 'null'::jsonb then
            v_item_source := v_source;
        end if;
        if jsonb_typeof(v_item_source) <> 'object' then
            raise exception 'Meal item source/provenance must be an object.' using errcode = '22023';
        end if;
        v_item_identity := v_item -> 'pricetrace_identity';
        if v_item_identity is null or v_item_identity = 'null'::jsonb then
            v_item_identity := v_identity;
        end if;
        if v_item_identity is not null then
            if jsonb_typeof(v_item_identity) <> 'object'
               or v_item_identity::text ~* 'https?://' then
                raise exception 'Meal item PriceTrace identity must be an object without URLs.'
                    using errcode = '23514';
            end if;
            v_namespace := lower(btrim(coalesce(
                v_item_identity ->> 'namespace',
                v_item_identity ->> 'source_namespace',
                ''
            )));
            if v_namespace <> '' and v_namespace <> 'pricetrace' then
                raise exception 'Meal item PriceTrace identity namespace must be pricetrace.'
                    using errcode = '23514';
            end if;
        end if;
        v_client_key := nullif(btrim(coalesce(v_item ->> 'client_key', '')), '');
        v_consumption_confidence := case
            when jsonb_typeof(v_item -> 'confidence') = 'number'
                then (v_item ->> 'confidence')::numeric
            else null
        end;
        v_item_id := gen_random_uuid()::text;
        insert into public.meal_record_items (
            id, owner_id, meal_record_id, nutrition_food_id, client_key,
            food_name_snapshot, brand_snapshot, manufacturer_name_snapshot,
            brand_name_snapshot, sub_brand_name_snapshot, product_name_snapshot,
            package_amount_snapshot, package_unit_snapshot, package_count_snapshot,
            food_kind_snapshot, consumed_amount, consumed_unit, consumption_confidence,
            quantity, unit,
            basis_amount_snapshot, basis_unit_snapshot, prep_state_snapshot,
            calories, protein_grams, carbs_grams, fat_grams, sodium_mg,
            saturated_fat_grams, sugars_grams, fiber_grams, added_sugars_grams,
            trans_fat_grams, cholesterol_mg, source_type_snapshot,
            source_reference_snapshot, source_version_snapshot, food_data_version_snapshot,
            source_provenance, pricetrace_identity, order_index
        ) values (
            v_item_id, v_user_id, v_meal_id, v_food.id, v_client_key,
            v_food.name, v_food.brand, null, null, null, null,
            null, null, null, v_food.kind, v_consumed_amount, v_consumed_unit,
            v_consumption_confidence,
            v_quantity, v_basis_unit, v_food.basis_amount, v_basis_unit, v_food.prep_state,
            case when v_food.calories_kcal is null then null else v_food.calories_kcal * v_scale end,
            case when v_food.protein_grams is null then null else v_food.protein_grams * v_scale end,
            case when v_food.carbs_grams is null then null else v_food.carbs_grams * v_scale end,
            case when v_food.fat_grams is null then null else v_food.fat_grams * v_scale end,
            case when v_food.sodium_mg is null then null else v_food.sodium_mg * v_scale end,
            case when v_food.saturated_fat_grams is null then null else v_food.saturated_fat_grams * v_scale end,
            case when v_food.sugars_grams is null then null else v_food.sugars_grams * v_scale end,
            case when v_food.fiber_grams is null then null else v_food.fiber_grams * v_scale end,
            case when v_food.added_sugars_grams is null then null else v_food.added_sugars_grams * v_scale end,
            case when v_food.trans_fat_grams is null then null else v_food.trans_fat_grams * v_scale end,
            case when v_food.cholesterol_mg is null then null else v_food.cholesterol_mg * v_scale end,
            v_food.source_type, v_food.source_reference, v_food.source_version,
            v_food.data_version, v_item_source,
            v_item_identity, v_index
        ) returning id into v_item_id;

        -- Use the returned item id so micronutrient rows cannot attach to another
        -- snapshot when a meal contains the same Nutrition food more than once.
        insert into public.meal_record_item_nutrients (
            id, owner_id, meal_record_id, meal_record_item_id, nutrient_code, amount, unit
        )
        select
            gen_random_uuid()::text,
            v_user_id,
            v_meal_id,
            v_item_id,
            nutrient.nutrient_code,
            nutrient.amount * v_scale,
            nutrient.unit
        from public.nutrition_food_nutrients nutrient
        where nutrient.food_id = v_food.id
         and nutrient.deleted_at is null
         and nutrient.amount is not null;
    end loop;

    return query select
        v_meal_import_id,
        v_meal_id,
        false,
        v_eaten_at_text,
        v_record_date,
        v_item_count,
        (select coalesce(array_agg(item.nutrition_food_id order by item.order_index), '{}'::text[])
         from public.meal_record_items item
         where item.meal_record_id = v_meal_id and item.owner_id = v_user_id),
        'fitness-meal-verified-import.v1';
end;
$$;

revoke all on function public.import_meal_component_estimate_v1(
    text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) from public, anon;
grant execute on function public.import_meal_component_estimate_v1(
    text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) to authenticated;

revoke all on function public.fitness_meal_normalize_unit_v1(text) from public, anon, authenticated;
revoke all on function public.fitness_meal_unit_dimension_v1(text) from public, anon, authenticated;
revoke all on function public.fitness_meal_unit_factor_v1(text) from public, anon, authenticated;
revoke all on function public.import_verified_meal_v1(text, text, jsonb, jsonb, jsonb)
    from public, anon;
grant execute on function public.import_verified_meal_v1(text, text, jsonb, jsonb, jsonb)
    to authenticated;

comment on function public.import_verified_meal_v1(text, text, jsonb, jsonb, jsonb) is
    'Authenticated yeonsik-ocr.v2 Meal boundary. Resolves exact owner/public Nutrition ids, preserves actual amount/unit, snapshots Nutrition values, accepts nullable restaurant menu identity, and replays identical idempotency payloads.';
comment on function public.import_meal_component_estimate_v1(
    text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) is
    'Authenticated meal_component_estimate NutritionFood boundary. Stores estimated values with per-nutrient evidence and permits a null restaurant_menu_id.';
comment on table public.meal_records is
    'Fitness-owned detailed Meal records written by the verified Meal ingest boundary; not part of Nutrition catalog sync.';
comment on table public.meal_record_items is
    'Immutable consumed Nutrition snapshots. nutrition_food_id is traceability only; displayed and aggregated values are the snapshot columns.';
comment on table public.meal_verified_imports is
    'Authenticated Meal ingest audit and idempotency bindings.';
