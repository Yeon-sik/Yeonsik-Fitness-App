-- Canonical, authenticated import boundary for the Yeonsik OCR nutrition pipeline.
-- The importer accepts only user-verified values and never publishes a food row.
-- Product labels and restaurant estimates share the point-value food table but keep
-- different source types and provenance contracts. Restaurant uncertainty lives in
-- a separate evidence table so it cannot be lost by the point-value projection.

create table if not exists public.nutrition_verified_imports (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    contract_version text not null
        check (contract_version = 'fitness-nutrition-verified-import.v1'),
    idempotency_key text not null
        check (length(btrim(idempotency_key)) between 1 and 200),
    source_document_ref text not null
        check (length(btrim(source_document_ref)) between 1 and 1000),
    evidence_type text not null
        check (evidence_type in ('product_label', 'restaurant_estimate')),
    food_name text not null check (length(btrim(food_name)) between 1 and 500),
    brand text,
    category text not null check (length(btrim(category)) between 1 and 100),
    basis_amount numeric not null check (basis_amount > 0),
    basis_unit text not null check (length(btrim(basis_unit)) between 1 and 30),
    required_nutrients jsonb not null,
    optional_nutrients jsonb not null default '{}'::jsonb,
    provenance jsonb not null,
    user_verified boolean not null check (user_verified),
    pricetrace_identity jsonb,
    catalog_key text not null,
    request_payload jsonb not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    estimation_evidence_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (owner_id, idempotency_key)
);

create table if not exists public.nutrition_verified_catalog_keys (
    owner_id text not null,
    catalog_key text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (owner_id, catalog_key),
    unique (nutrition_food_id)
);

create table if not exists public.nutrition_estimation_evidence (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    import_id uuid not null references public.nutrition_verified_imports(id) on delete restrict,
    evidence_type text not null check (evidence_type = 'restaurant_estimate'),
    estimated boolean not null check (estimated),
    confidence numeric not null check (confidence >= 0 and confidence <= 1),
    point_values jsonb not null,
    uncertainty_range jsonb not null default '{}'::jsonb,
    evidence_payload jsonb not null,
    created_at timestamptz not null default now(),
    unique (import_id)
);

alter table public.nutrition_verified_imports
    add column if not exists estimation_evidence_id uuid;

create index if not exists nutrition_verified_imports_food_idx
    on public.nutrition_verified_imports (owner_id, nutrition_food_id, created_at desc);
create index if not exists nutrition_estimation_evidence_food_idx
    on public.nutrition_estimation_evidence (owner_id, nutrition_food_id, created_at desc);

alter table public.nutrition_verified_imports enable row level security;
alter table public.nutrition_verified_catalog_keys enable row level security;
alter table public.nutrition_estimation_evidence enable row level security;

grant select on public.nutrition_verified_imports to authenticated;
grant select on public.nutrition_verified_catalog_keys to authenticated;
grant select on public.nutrition_estimation_evidence to authenticated;
revoke insert, update, delete on public.nutrition_verified_imports
    from anon, authenticated;
revoke insert, update, delete on public.nutrition_verified_catalog_keys
    from anon, authenticated;
revoke insert, update, delete on public.nutrition_estimation_evidence
    from anon, authenticated;

drop policy if exists nutrition_verified_imports_select
    on public.nutrition_verified_imports;
create policy nutrition_verified_imports_select
    on public.nutrition_verified_imports
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists nutrition_verified_catalog_keys_select
    on public.nutrition_verified_catalog_keys;
create policy nutrition_verified_catalog_keys_select
    on public.nutrition_verified_catalog_keys
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists nutrition_estimation_evidence_select
    on public.nutrition_estimation_evidence;
create policy nutrition_estimation_evidence_select
    on public.nutrition_estimation_evidence
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

create or replace function public.import_verified_nutrition_v1(
    p_idempotency_key text,
    p_source_document_ref text,
    p_evidence_type text,
    p_food_name text,
    p_brand text,
    p_category text,
    p_basis_amount numeric,
    p_basis_unit text,
    p_required_nutrients jsonb,
    p_optional_nutrients jsonb default '{}'::jsonb,
    p_provenance jsonb default '{}'::jsonb,
    p_user_verified boolean default false,
    p_pricetrace_identity jsonb default null,
    p_estimation_evidence jsonb default null
)
returns table (
    import_id uuid,
    idempotent_replay boolean,
    nutrition_food_id text,
    evidence_type text,
    source_type text,
    kind text,
    data_version integer,
    visibility text,
    catalog_key text,
    catalog_product_id uuid,
    estimation_evidence_id uuid,
    link_created boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_evidence_type text := lower(btrim(coalesce(p_evidence_type, '')));
    v_source_document_ref text := btrim(coalesce(p_source_document_ref, ''));
    v_food_name text := btrim(coalesce(p_food_name, ''));
    v_brand text := nullif(btrim(coalesce(p_brand, '')), '');
    v_category text := lower(btrim(coalesce(p_category, '')));
    v_basis_unit text := lower(btrim(coalesce(p_basis_unit, '')));
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_restaurant_name text := nullif(btrim(coalesce(p_provenance ->> 'restaurant_name', '')), '');
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
    v_existing public.nutrition_verified_imports%rowtype;
    v_food_id text;
    v_import_id uuid := gen_random_uuid();
    v_estimation_evidence_id uuid;
    v_catalog_product_id uuid;
    v_catalog_key text;
    v_source_type text;
    v_kind text := 'external_menu';
    v_prep_state text;
    v_link_created boolean := false;
    v_confidence numeric;
    v_range jsonb := '{}'::jsonb;
    v_key text;
    v_number numeric;
    v_existing_link public.product_nutrition_links%rowtype;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.'
            using errcode = '42514';
    end if;
    if v_evidence_type not in ('product_label', 'restaurant_estimate') then
        raise exception 'evidence_type must be product_label or restaurant_estimate.'
            using errcode = '22023';
    end if;
    if length(btrim(coalesce(p_idempotency_key, ''))) = 0 then
        raise exception 'An idempotency key is required.' using errcode = '22023';
    end if;
    if length(v_source_document_ref) = 0 then
        raise exception 'A source document reference is required.' using errcode = '22023';
    end if;
    if length(v_food_name) = 0 or length(v_category) = 0 or p_basis_amount is null
       or p_basis_amount <= 0 or length(v_basis_unit) = 0 then
        raise exception 'Food name, category, and a positive Nutrition basis are required.'
            using errcode = '22023';
    end if;
    if v_category not in ('meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable', 'fruit',
                         'legume', 'dairy', 'nut_seed', 'processed', 'beverage', 'recipe', 'other') then
        raise exception 'category is not supported by the Nutrition catalog.' using errcode = '22023';
    end if;
    if v_basis_unit not in ('g', 'mg', 'kg', 'ml', 'l', 'serving', 'portion', 'pack', 'each', '개') then
        raise exception 'basis_unit is not supported by the Nutrition catalog.'
            using errcode = '22023';
    end if;
    if jsonb_typeof(v_required) <> 'object'
       or jsonb_typeof(v_optional) <> 'object'
       or jsonb_typeof(v_provenance) <> 'object' then
        raise exception 'Required nutrients, optional nutrients, and provenance must be objects.'
            using errcode = '22023';
    end if;

    foreach v_key in array ARRAY[
        'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
        'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
    ] loop
        if not (v_required ? v_key)
           or jsonb_typeof(v_required -> v_key) <> 'number' then
            raise exception 'All seven required Nutrition values are required.'
                using errcode = '23514', detail = v_key;
        end if;
        v_number := (v_required ->> v_key)::numeric;
        if v_number < 0 then
            raise exception 'Nutrition values cannot be negative.'
                using errcode = '23514', detail = v_key;
        end if;
    end loop;

    foreach v_key in array ARRAY[
        'fiber_grams', 'added_sugars_grams', 'trans_fat_grams', 'cholesterol_mg'
    ] loop
        if v_optional ? v_key then
            if jsonb_typeof(v_optional -> v_key) <> 'number'
               or (v_optional ->> v_key)::numeric < 0 then
                raise exception 'Optional Nutrition values must be non-negative numbers.'
                    using errcode = '22023', detail = v_key;
            end if;
        end if;
    end loop;

    if v_evidence_type = 'product_label' then
        v_source_type := 'product_label';
        v_prep_state := 'unspecified';
        if v_provenance ? 'estimated'
           and (v_provenance ->> 'estimated')::boolean then
            raise exception 'A product label import cannot be estimated.' using errcode = '23514';
        end if;
        if v_estimation is not null then
            raise exception 'Restaurant estimation evidence is not valid for a product label.'
                using errcode = '22023';
        end if;
        if v_identity is not null then
            if jsonb_typeof(v_identity) <> 'object'
               or coalesce(v_identity ->> 'namespace', '') <> 'pricetrace'
               or nullif(v_identity ->> 'catalog_product_id', '') is null
               or v_identity ? 'restaurant_id'
               or v_identity ? 'restaurant_location_id'
               or v_identity ? 'restaurant_menu_id' then
                raise exception 'A product label may carry only a resolved PriceTrace catalog_product_id.'
                    using errcode = '23514';
            end if;
            begin
                v_catalog_product_id := (v_identity ->> 'catalog_product_id')::uuid;
            exception when others then
                raise exception 'catalog_product_id must be a UUID.' using errcode = '22023';
            end;
        end if;
    else
        v_source_type := 'manual_estimate';
        v_prep_state := 'as_served';
        v_brand := coalesce(v_brand, v_restaurant_name);
        if v_brand is null then
            raise exception 'Restaurant estimates require a restaurant name in brand or provenance.' using errcode = '22023';
        end if;
        if coalesce(v_provenance ->> 'estimated', '') <> 'true' then
            raise exception 'restaurant_estimate provenance must set estimated=true.'
                using errcode = '23514';
        end if;
        if v_estimation is null or jsonb_typeof(v_estimation) <> 'object' then
            raise exception 'Restaurant estimates require confidence evidence.'
                using errcode = '23514';
        end if;
        if jsonb_typeof(v_estimation -> 'confidence') <> 'number' then
            raise exception 'Restaurant estimates require numeric confidence.'
                using errcode = '23514';
        end if;
        v_confidence := (v_estimation ->> 'confidence')::numeric;
        if v_confidence < 0 or v_confidence > 1 then
            raise exception 'Estimation confidence must be between 0 and 1.'
                using errcode = '23514';
        end if;
        if v_estimation ? 'range' then
            if jsonb_typeof(v_estimation -> 'range') <> 'object' then
                raise exception 'Estimation range must be an object.' using errcode = '22023';
            end if;
            v_range := v_estimation -> 'range';
            foreach v_key in array ARRAY[
                'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
                'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
            ] loop
                if v_range ? v_key then
                    if jsonb_typeof(v_range -> v_key) <> 'object'
                       or jsonb_typeof(v_range -> v_key -> 'min') <> 'number'
                       or jsonb_typeof(v_range -> v_key -> 'point') <> 'number'
                       or jsonb_typeof(v_range -> v_key -> 'max') <> 'number'
                       or (v_range -> v_key ->> 'min')::numeric < 0
                       or (v_range -> v_key ->> 'min')::numeric > (v_range -> v_key ->> 'point')::numeric
                       or (v_range -> v_key ->> 'point')::numeric <> (v_required ->> v_key)::numeric
                       or (v_range -> v_key ->> 'point')::numeric > (v_range -> v_key ->> 'max')::numeric then
                        raise exception 'Each estimation range must contain min, point, max in order.'
                            using errcode = '23514', detail = v_key;
                    end if;
                end if;
            end loop;
        end if;
        if v_identity is not null then
            if jsonb_typeof(v_identity) <> 'object'
               or coalesce(v_identity ->> 'schema_version', v_identity ->> 'contract_version', '')
                    <> 'dining-out-identity.v1'
               or coalesce(v_identity ->> 'namespace', '') <> 'pricetrace'
               or nullif(v_identity ->> 'restaurant_id', '') is null
               or nullif(v_identity ->> 'restaurant_location_id', '') is null
               or nullif(v_identity ->> 'restaurant_menu_id', '') is null
               or nullif(v_identity ->> 'catalog_product_id', '') is null then
                raise exception 'Restaurant links require the complete exact PriceTrace identity.'
                    using errcode = '23514';
            end if;
            begin
                perform (v_identity ->> 'restaurant_id')::uuid;
                perform (v_identity ->> 'restaurant_location_id')::uuid;
                perform (v_identity ->> 'restaurant_menu_id')::uuid;
                v_catalog_product_id := (v_identity ->> 'catalog_product_id')::uuid;
            exception when others then
                raise exception 'PriceTrace restaurant identity values must be UUIDs.'
                    using errcode = '22023';
            end;
        end if;
    end if;

    if coalesce(v_provenance ->> 'source_type', v_source_type) <> v_source_type then
        raise exception 'Provenance source_type does not match evidence_type.' using errcode = '23514';
    end if;

    v_catalog_key := v_evidence_type || ':' || case
        when v_catalog_product_id is not null then 'pricetrace:' || v_catalog_product_id::text
        else 'local:' || lower(regexp_replace(
            concat_ws('|', coalesce(v_brand, ''), v_food_name, v_category,
                p_basis_amount::text, v_basis_unit), '\s+', ' ', 'g'))
    end;
    v_request_payload := jsonb_build_object(
        'contract_version', 'fitness-nutrition-verified-import.v1',
        'idempotency_key', btrim(p_idempotency_key),
        'source_document_ref', v_source_document_ref,
        'evidence_type', v_evidence_type,
        'food_name', v_food_name,
        'brand', v_brand,
        'category', v_category,
        'basis_amount', p_basis_amount,
        'basis_unit', v_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', coalesce(v_identity, 'null'::jsonb),
        'estimation_evidence', coalesce(v_estimation, 'null'::jsonb)
    );

    -- Serialize retries of the same idempotency key before the uniqueness check.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':idempotency:' || btrim(p_idempotency_key), 0)
    );
    select * into v_existing
    from public.nutrition_verified_imports
    where owner_id = v_user_id
      and idempotency_key = btrim(p_idempotency_key)
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different payload.'
                using errcode = '23505';
        end if;
        return query select
            v_existing.id, true, v_existing.nutrition_food_id, v_existing.evidence_type,
            case when v_existing.evidence_type = 'product_label' then 'product_label' else 'manual_estimate' end,
            'external_menu', 2, 'private', v_existing.catalog_key,
            case when v_existing.pricetrace_identity is null then null
                 else (v_existing.pricetrace_identity ->> 'catalog_product_id')::uuid end,
            v_existing.estimation_evidence_id, false;
        return;
    end if;

    -- Serialize different ingestions of the same catalog key without changing the
    -- identity already established by a prior verified import.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':' || v_catalog_key, 0)
    );
    select nutrition_food_id into v_food_id
    from public.nutrition_verified_catalog_keys
    where owner_id = v_user_id and catalog_key = v_catalog_key
    for update;

    if v_food_id is null then
        v_food_id := gen_random_uuid()::text;
        insert into public.nutrition_foods (
            id, owner_id, name, brand, kind, category, basis_amount, basis_unit,
            prep_state, calories_kcal, protein_grams, carbs_grams, fat_grams,
            sodium_mg, saturated_fat_grams, sugars_grams, fiber_grams,
            added_sugars_grams, trans_fat_grams, cholesterol_mg, source_type,
            source_reference, source_version, data_version, visibility,
            created_at, updated_at, deleted_at
        ) values (
            v_food_id, v_user_id, v_food_name, v_brand, v_kind, v_category,
            p_basis_amount, v_basis_unit, v_prep_state,
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
            v_source_type, null, nullif(v_provenance ->> 'source_version', ''), 2,
            'private', now(), now(), null
        );
        insert into public.nutrition_verified_catalog_keys (
            owner_id, catalog_key, nutrition_food_id
        ) values (v_user_id, v_catalog_key, v_food_id);
    else
        update public.nutrition_foods
        set name = v_food_name,
            brand = v_brand,
            category = v_category,
            basis_amount = p_basis_amount,
            basis_unit = v_basis_unit,
            prep_state = v_prep_state,
            calories_kcal = (v_required ->> 'calories_kcal')::numeric,
            protein_grams = (v_required ->> 'protein_grams')::numeric,
            carbs_grams = (v_required ->> 'carbs_grams')::numeric,
            fat_grams = (v_required ->> 'fat_grams')::numeric,
            sodium_mg = (v_required ->> 'sodium_mg')::numeric,
            saturated_fat_grams = (v_required ->> 'saturated_fat_grams')::numeric,
            sugars_grams = (v_required ->> 'sugars_grams')::numeric,
            fiber_grams = nullif(v_optional ->> 'fiber_grams', '')::numeric,
            added_sugars_grams = nullif(v_optional ->> 'added_sugars_grams', '')::numeric,
            trans_fat_grams = nullif(v_optional ->> 'trans_fat_grams', '')::numeric,
            cholesterol_mg = nullif(v_optional ->> 'cholesterol_mg', '')::numeric,
            source_type = v_source_type,
            source_version = nullif(v_provenance ->> 'source_version', ''),
            data_version = 2,
            visibility = 'private',
            updated_at = now()
        where id = v_food_id and owner_id = v_user_id and deleted_at is null;
        if not found then
            raise exception 'The Nutrition catalog mapping points to an unavailable food.'
                using errcode = 'P0002';
        end if;
    end if;

    if v_evidence_type = 'restaurant_estimate' then
        v_estimation_evidence_id := gen_random_uuid();
    end if;

    insert into public.nutrition_verified_imports (
        id, owner_id, contract_version, idempotency_key, source_document_ref,
        evidence_type, food_name, brand, category, basis_amount, basis_unit,
        required_nutrients, optional_nutrients, provenance, user_verified,
        pricetrace_identity, catalog_key, request_payload, nutrition_food_id,
        estimation_evidence_id
    ) values (
        v_import_id, v_user_id, 'fitness-nutrition-verified-import.v1',
        btrim(p_idempotency_key), v_source_document_ref, v_evidence_type,
        v_food_name, v_brand, v_category, p_basis_amount, v_basis_unit,
        v_required, v_optional, v_provenance, true, v_identity, v_catalog_key,
        v_request_payload, v_food_id, v_estimation_evidence_id
    );

    if v_evidence_type = 'restaurant_estimate' then
        insert into public.nutrition_estimation_evidence (
            id, owner_id, nutrition_food_id, import_id, evidence_type, estimated,
            confidence, point_values, uncertainty_range, evidence_payload
        ) values (
            v_estimation_evidence_id, v_user_id, v_food_id, v_import_id,
            'restaurant_estimate', true, v_confidence, v_required, v_range, v_estimation
        );
    end if;

    update public.nutrition_foods
    set source_reference = case
        when v_evidence_type = 'restaurant_estimate' then jsonb_build_object(
            -- Keep the existing top-level dining-out identity contract. The
            -- publication RPC and DiningOutIdentity reader consume these keys.
            'schema_version', 'dining-out-identity.v1',
            'namespace', coalesce(v_identity ->> 'namespace', 'fitnessapp'),
            'restaurant_id', coalesce(v_identity -> 'restaurant_id', 'null'::jsonb),
            'restaurant_name', v_brand,
            'restaurant_location_id', coalesce(v_identity -> 'restaurant_location_id', 'null'::jsonb),
            'source_location_code', coalesce(v_identity -> 'source_location_code', 'null'::jsonb),
            'branch_name', coalesce(v_identity -> 'branch_name', 'null'::jsonb),
            'restaurant_menu_id', coalesce(v_identity -> 'restaurant_menu_id', 'null'::jsonb),
            'menu_name', v_food_name,
            'catalog_product_id', coalesce(v_identity -> 'catalog_product_id', 'null'::jsonb),
            'contract_version', 'fitness-nutrition-verified-import.v1',
            'evidence_type', v_evidence_type,
            'source_document_ref', v_source_document_ref,
            'user_verified', true,
            'provenance', v_provenance,
            'pricetrace_identity', coalesce(v_identity, 'null'::jsonb),
            'estimation_evidence_id', v_estimation_evidence_id::text
        )::text
        else jsonb_build_object(
            'contract_version', 'fitness-nutrition-verified-import.v1',
            'evidence_type', v_evidence_type,
            'source_document_ref', v_source_document_ref,
            'user_verified', true,
            'provenance', v_provenance,
            'pricetrace_identity', coalesce(v_identity, 'null'::jsonb),
            'estimation_evidence_id', v_estimation_evidence_id::text
        )::text
    end,
        updated_at = now()
    where id = v_food_id and owner_id = v_user_id;

    if v_catalog_product_id is not null then
        select * into v_existing_link
        from public.product_nutrition_links
        where owner_id = v_user_id
          and nutrition_food_id = v_food_id
          and status = 'approved'
          and deleted_at is null
        order by created_at desc
        limit 1
        for update;
        if found and v_existing_link.catalog_product_id <> v_catalog_product_id then
            raise exception 'The Nutrition food already has a different approved PriceTrace link.'
                using errcode = '23505';
        end if;
        if not found then
            insert into public.product_nutrition_links (
                owner_id, nutrition_food_id, catalog_product_id, status,
                source_type, proposal_reference, product_contract_version,
                revision, reviewed_at, created_at, updated_at, deleted_at
            ) values (
                v_user_id, v_food_id, v_catalog_product_id, 'approved',
                'manual_selection',
                'fitness-nutrition-verified-import:' || v_import_id::text,
                'product-read.v1', 1, now(), now(), now(), null
            );
            v_link_created := true;
        end if;
    end if;

    return query select
        v_import_id, false, v_food_id, v_evidence_type, v_source_type,
        v_kind, 2, 'private', v_catalog_key, v_catalog_product_id,
        v_estimation_evidence_id, v_link_created;
end;
$$;

revoke all on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) from public, anon;
grant execute on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) to authenticated;

comment on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) is
    'Authenticated OCR import boundary. Requires user_verified=true and all seven required values, stores private Fitness-owned Nutrition, preserves restaurant uncertainty, and links only supplied exact PriceTrace identity.';

comment on table public.nutrition_verified_imports is
    'Immutable-ish audit and idempotency record for user-verified OCR Nutrition imports.';
comment on table public.nutrition_estimation_evidence is
    'Restaurant estimate confidence and uncertainty evidence; point values are copied to nutrition_foods but range evidence remains here.';
