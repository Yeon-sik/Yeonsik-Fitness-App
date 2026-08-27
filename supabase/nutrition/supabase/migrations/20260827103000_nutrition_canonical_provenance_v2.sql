-- Canonical provenance is intentionally separate from the nutrition_foods projection.
-- The legacy verified importer remains the projection executor for OCR App compatibility;
-- this v2 boundary owns the input contract and per-nutrient observed/estimated evidence.

create table if not exists public.nutrition_canonical_imports (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    input_contract text not null check (input_contract in ('nutrition-label.v1', 'food-estimate.v1')),
    idempotency_key text not null check (length(btrim(idempotency_key)) between 1 and 200),
    source_document_ref text not null check (length(btrim(source_document_ref)) between 1 and 1000),
    user_verified boolean not null check (user_verified),
    required_nutrients jsonb not null,
    optional_nutrients jsonb not null default '{}'::jsonb,
    nutrient_provenance jsonb not null,
    provenance jsonb not null default '{}'::jsonb,
    pricetrace_identity jsonb,
    request_payload jsonb not null,
    projection_import_id uuid not null references public.nutrition_verified_imports(id) on delete restrict,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    projection_source_type text not null check (projection_source_type in ('product_label_ocr', 'food_image_estimate')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (owner_id, idempotency_key),
    unique (projection_import_id)
);

create table if not exists public.nutrition_food_nutrient_provenance (
    id uuid primary key default gen_random_uuid(),
    owner_id text not null,
    nutrition_food_id text not null references public.nutrition_foods(id) on delete restrict,
    canonical_import_id uuid not null references public.nutrition_canonical_imports(id) on delete restrict,
    nutrient_code text not null check (nutrient_code in (
        'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
        'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
    )),
    value numeric not null check (value >= 0),
    value_status text not null check (value_status in ('observed', 'estimated')),
    source_type text not null check (source_type in (
        'product_label_ocr', 'food_image_estimate', 'menu_reference', 'manual'
    )),
    evidence_refs jsonb not null check (jsonb_typeof(evidence_refs) = 'array'),
    confidence numeric check (confidence is null or (confidence >= 0 and confidence <= 1)),
    uncertainty_range jsonb,
    created_at timestamptz not null default now(),
    unique (canonical_import_id, nutrient_code)
);

create index if not exists nutrition_canonical_imports_food_idx
    on public.nutrition_canonical_imports (owner_id, nutrition_food_id, created_at desc);
create index if not exists nutrition_food_nutrient_provenance_food_idx
    on public.nutrition_food_nutrient_provenance (owner_id, nutrition_food_id, created_at desc);

alter table public.nutrition_canonical_imports enable row level security;
alter table public.nutrition_food_nutrient_provenance enable row level security;

drop policy if exists nutrition_canonical_imports_select on public.nutrition_canonical_imports;
create policy nutrition_canonical_imports_select on public.nutrition_canonical_imports
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));
drop policy if exists nutrition_food_nutrient_provenance_select on public.nutrition_food_nutrient_provenance;
create policy nutrition_food_nutrient_provenance_select on public.nutrition_food_nutrient_provenance
    for select to authenticated
    using (owner_id = ((select auth.uid())::text));

grant select on public.nutrition_canonical_imports, public.nutrition_food_nutrient_provenance to authenticated;
revoke insert, update, delete on public.nutrition_canonical_imports, public.nutrition_food_nutrient_provenance from anon, authenticated;

create or replace function public.import_canonical_nutrition_v2(
    p_idempotency_key text,
    p_input_contract text,
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
    canonical_import_id uuid,
    idempotent_replay boolean,
    nutrition_food_id text,
    input_contract text,
    projection_source_type text,
    projection_import_id uuid,
    catalog_product_id uuid,
    estimation_evidence_id uuid,
    visibility text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_contract text := lower(btrim(coalesce(p_input_contract, '')));
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_nutrient_provenance jsonb := coalesce(p_nutrient_provenance, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_source_document_ref text := btrim(coalesce(p_source_document_ref, ''));
    v_projection_source_type text;
    v_legacy_evidence_type text;
    v_legacy_source_type text;
    v_legacy_provenance jsonb;
    v_request_payload jsonb;
    v_existing public.nutrition_canonical_imports%rowtype;
    v_canonical_import_id uuid := gen_random_uuid();
    v_projection record;
    v_key text;
    v_item jsonb;
    v_value_status text;
    v_nutrient_source_type text;
    v_has_estimated boolean := false;
    v_confidence numeric;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.' using errcode = '42514';
    end if;
    if v_contract not in ('nutrition-label.v1', 'food-estimate.v1') then
        raise exception 'input_contract must be nutrition-label.v1 or food-estimate.v1.' using errcode = '22023';
    end if;
    if length(btrim(coalesce(p_idempotency_key, ''))) = 0 or length(v_source_document_ref) = 0 then
        raise exception 'An idempotency key and source document reference are required.' using errcode = '22023';
    end if;
    if jsonb_typeof(v_required) <> 'object'
       or jsonb_typeof(v_optional) <> 'object'
       or jsonb_typeof(v_nutrient_provenance) <> 'object'
       or jsonb_typeof(v_provenance) <> 'object' then
        raise exception 'Nutrients and provenance must be objects.' using errcode = '22023';
    end if;

    foreach v_key in array ARRAY[
        'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
        'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
    ] loop
        v_item := v_nutrient_provenance -> v_key;
        if jsonb_typeof(v_required -> v_key) <> 'number'
           or jsonb_typeof(v_item) <> 'object'
           or jsonb_typeof(v_item -> 'value') <> 'number'
           or (v_item ->> 'value')::numeric <> (v_required ->> v_key)::numeric
           or jsonb_typeof(v_item -> 'evidence_refs') <> 'array'
           or jsonb_array_length(v_item -> 'evidence_refs') = 0 then
            raise exception 'Every required nutrient needs a matching value and at least one evidence reference.'
                using errcode = '23514', detail = v_key;
        end if;
        v_value_status := lower(btrim(coalesce(v_item ->> 'value_status', '')));
        v_nutrient_source_type := lower(btrim(coalesce(v_item ->> 'source_type', '')));
        if v_value_status not in ('observed', 'estimated')
           or v_nutrient_source_type not in ('product_label_ocr', 'food_image_estimate', 'menu_reference', 'manual') then
            raise exception 'Each nutrient must declare a valid value_status and source_type.'
                using errcode = '23514', detail = v_key;
        end if;
        if v_contract = 'nutrition-label.v1'
           and (v_value_status <> 'observed' or v_nutrient_source_type <> 'product_label_ocr') then
            raise exception 'nutrition-label.v1 accepts only observed product_label_ocr nutrient values.'
                using errcode = '23514', detail = v_key;
        end if;
        if v_contract = 'food-estimate.v1'
           and v_nutrient_source_type = 'product_label_ocr' then
            raise exception 'food-estimate.v1 cannot claim product-label nutrient evidence.'
                using errcode = '23514', detail = v_key;
        end if;
        v_has_estimated := v_has_estimated or v_value_status = 'estimated';
    end loop;

    if v_contract = 'food-estimate.v1' and not v_has_estimated then
        raise exception 'food-estimate.v1 requires at least one estimated nutrient value.' using errcode = '23514';
    end if;
    if v_contract = 'nutrition-label.v1' and coalesce(v_provenance ->> 'estimated', 'false') <> 'false' then
        raise exception 'nutrition-label.v1 cannot be estimated.' using errcode = '23514';
    end if;

    if v_contract = 'nutrition-label.v1' then
        v_projection_source_type := 'product_label_ocr';
        v_legacy_evidence_type := 'product_label';
        v_legacy_source_type := 'product_label';
        if p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
            raise exception 'nutrition-label.v1 cannot contain estimation evidence.' using errcode = '22023';
        end if;
    else
        v_projection_source_type := 'food_image_estimate';
        v_legacy_evidence_type := 'restaurant_estimate';
        v_legacy_source_type := 'manual_estimate';
        if p_estimation_evidence is null
           or p_estimation_evidence = 'null'::jsonb
           or jsonb_typeof(p_estimation_evidence -> 'confidence') <> 'number' then
            raise exception 'food-estimate.v1 requires numeric confidence evidence.' using errcode = '23514';
        end if;
        v_confidence := (p_estimation_evidence ->> 'confidence')::numeric;
        if v_confidence < 0 or v_confidence > 1 then
            raise exception 'Estimation confidence must be between 0 and 1.' using errcode = '23514';
        end if;
    end if;

    v_legacy_provenance := v_provenance || jsonb_build_object(
        'source_type', v_legacy_source_type,
        'canonical_input_contract', v_contract,
        'estimated', v_contract = 'food-estimate.v1'
    );
    v_request_payload := jsonb_build_object(
        'contract_version', 'fitness-nutrition-canonical-import.v2',
        'idempotency_key', btrim(p_idempotency_key),
        'input_contract', v_contract,
        'source_document_ref', v_source_document_ref,
        'food_name', p_food_name,
        'brand', p_brand,
        'category', p_category,
        'basis_amount', p_basis_amount,
        'basis_unit', p_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'nutrient_provenance', v_nutrient_provenance,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', coalesce(p_pricetrace_identity, 'null'::jsonb),
        'estimation_evidence', coalesce(p_estimation_evidence, 'null'::jsonb)
    );

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':canonical-nutrition:' || btrim(p_idempotency_key), 0)
    );
    select * into v_existing
    from public.nutrition_canonical_imports
    where owner_id = v_user_id and idempotency_key = btrim(p_idempotency_key)
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different canonical payload.' using errcode = '23505';
        end if;
        return query select v_existing.id, true, v_existing.nutrition_food_id,
            v_existing.input_contract, v_existing.projection_source_type,
            v_existing.projection_import_id,
            case when v_existing.pricetrace_identity is null then null
                 else (v_existing.pricetrace_identity ->> 'catalog_product_id')::uuid end,
            (select estimation_evidence_id from public.nutrition_verified_imports where id = v_existing.projection_import_id),
            'private';
        return;
    end if;

    select * into v_projection
    from public.import_verified_nutrition_v1(
        btrim(p_idempotency_key), v_source_document_ref, v_legacy_evidence_type,
        p_food_name, p_brand, p_category, p_basis_amount, p_basis_unit,
        v_required, v_optional, v_legacy_provenance, true,
        p_pricetrace_identity, p_estimation_evidence
    );

    -- nutrition_foods is deliberately only the final point-value projection.
    -- Canonical source semantics stay in the two auxiliary provenance tables.
    update public.nutrition_foods
    set source_type = v_projection_source_type,
        source_version = 'nutrition-projection.v2',
        visibility = 'private',
        updated_at = now()
    where id = v_projection.nutrition_food_id
      and owner_id = v_user_id
      and deleted_at is null;

    insert into public.nutrition_canonical_imports (
        id, owner_id, input_contract, idempotency_key, source_document_ref,
        user_verified, required_nutrients, optional_nutrients, nutrient_provenance,
        provenance, pricetrace_identity, request_payload, projection_import_id,
        nutrition_food_id, projection_source_type
    ) values (
        v_canonical_import_id, v_user_id, v_contract, btrim(p_idempotency_key),
        v_source_document_ref, true, v_required, v_optional, v_nutrient_provenance,
        v_provenance, p_pricetrace_identity, v_request_payload, v_projection.import_id,
        v_projection.nutrition_food_id, v_projection_source_type
    );

    insert into public.nutrition_food_nutrient_provenance (
        owner_id, nutrition_food_id, canonical_import_id, nutrient_code, value,
        value_status, source_type, evidence_refs, confidence, uncertainty_range
    )
    select
        v_user_id,
        v_projection.nutrition_food_id,
        v_canonical_import_id,
        key,
        (value ->> 'value')::numeric,
        value ->> 'value_status',
        value ->> 'source_type',
        value -> 'evidence_refs',
        case when v_contract = 'food-estimate.v1' then v_confidence else null end,
        case when v_contract = 'food-estimate.v1' then p_estimation_evidence -> 'range' -> key else null end
    from jsonb_each(v_nutrient_provenance);

    return query select
        v_canonical_import_id, false, v_projection.nutrition_food_id, v_contract,
        v_projection_source_type, v_projection.import_id, v_projection.catalog_product_id,
        v_projection.estimation_evidence_id, 'private';
end;
$$;

revoke all on function public.import_canonical_nutrition_v2(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) from public, anon;
grant execute on function public.import_canonical_nutrition_v2(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) to authenticated;

-- Existing owner publication/link RPCs keep their names and exact identity checks.
-- They now also accept the canonical food_image_estimate projection source type.
create or replace function public.set_dining_out_menu_publication_v1(
    p_nutrition_food_id text, p_publish boolean
)
returns table (
    nutrition_food_id text, restaurant_id uuid, restaurant_location_id uuid,
    restaurant_menu_id uuid, catalog_product_id uuid, visibility text,
    publication_revision integer, published_at timestamptz, updated_at timestamptz
)
language plpgsql security definer set search_path = ''
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
    if v_user_id is null then raise exception 'Authentication is required.' using errcode = '42501'; end if;
    select food.* into v_food from public.nutrition_foods food
    where food.id = p_nutrition_food_id and food.owner_id = v_user_id
      and food.kind = 'external_menu'
      and food.source_type in ('manual_estimate', 'food_image_estimate')
      and food.deleted_at is null for update;
    if not found then raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002'; end if;
    if p_publish then
        begin
            v_identity := v_food.source_reference::jsonb;
            v_restaurant_id := (v_identity ->> 'restaurant_id')::uuid;
            v_location_id := (v_identity ->> 'restaurant_location_id')::uuid;
            v_menu_id := (v_identity ->> 'restaurant_menu_id')::uuid;
            v_catalog_product_id := (v_identity ->> 'catalog_product_id')::uuid;
        exception when others then
            raise exception 'An exact PriceTrace restaurant, location, menu, and catalog product identity is required.' using errcode = '23514';
        end;
        if coalesce(v_identity ->> 'schema_version', v_identity ->> 'contract_version', '') <> 'dining-out-identity.v1'
           or v_restaurant_id is null or v_location_id is null or v_menu_id is null or v_catalog_product_id is null
           or v_food.calories_kcal is null or v_food.protein_grams is null or v_food.carbs_grams is null or v_food.fat_grams is null then
            raise exception 'An exact PriceTrace identity and dining-out macro profile are required for publication.' using errcode = '23514';
        end if;
    else
        begin
            v_identity := v_food.source_reference::jsonb;
            v_restaurant_id := nullif(v_identity ->> 'restaurant_id', '')::uuid;
            v_location_id := nullif(v_identity ->> 'restaurant_location_id', '')::uuid;
            v_menu_id := nullif(v_identity ->> 'restaurant_menu_id', '')::uuid;
            v_catalog_product_id := nullif(v_identity ->> 'catalog_product_id', '')::uuid;
        exception when others then
            v_restaurant_id := null; v_location_id := null; v_menu_id := null; v_catalog_product_id := null;
        end;
    end if;
    update public.nutrition_foods food set visibility = case when p_publish then 'public' else 'private' end,
        publication_revision = food.publication_revision + 1,
        published_at = case when p_publish then v_now else null end,
        published_by = case when p_publish then v_user_id else null end, updated_at = v_now
    where food.id = p_nutrition_food_id returning food.* into v_food;
    v_snapshot := jsonb_build_object('contract_version', 'dining-out-publication.v1', 'nutrition_food_id', v_food.id,
        'restaurant_id', v_restaurant_id, 'restaurant_location_id', v_location_id, 'restaurant_menu_id', v_menu_id,
        'catalog_product_id', v_catalog_product_id, 'restaurant_name', v_food.brand, 'menu_name', v_food.name,
        'nutrition_values', jsonb_build_object('calories_kcal', v_food.calories_kcal, 'protein_grams', v_food.protein_grams,
            'carbs_grams', v_food.carbs_grams, 'fat_grams', v_food.fat_grams, 'sodium_mg', v_food.sodium_mg,
            'saturated_fat_grams', v_food.saturated_fat_grams, 'sugars_grams', v_food.sugars_grams));
    insert into public.nutrition_dining_out_publication_events (nutrition_food_id, owner_id, restaurant_id,
        restaurant_location_id, restaurant_menu_id, catalog_product_id, action, food_revision, publication_revision, nutrition_snapshot)
    values (v_food.id, v_user_id, v_restaurant_id, v_location_id, v_menu_id, v_catalog_product_id,
        case when p_publish then 'publish' else 'unpublish' end, v_food.revision, v_food.publication_revision, v_snapshot);
    return query select v_food.id, v_restaurant_id, v_location_id, v_menu_id, v_catalog_product_id,
        v_food.visibility, v_food.publication_revision, v_food.published_at, v_food.updated_at;
end;
$$;

create or replace function public.attach_dining_out_menu_identity_v1(
    p_nutrition_food_id text, p_restaurant_id uuid, p_restaurant_location_id uuid,
    p_restaurant_menu_id uuid, p_catalog_product_id uuid
)
returns table (nutrition_food_id text, source_reference text, updated_at timestamptz)
language plpgsql security definer set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_food public.nutrition_foods%rowtype;
    v_identity jsonb;
    v_updated_at timestamptz := now();
begin
    if v_user_id is null then raise exception 'Authentication is required.' using errcode = '42501'; end if;
    if p_restaurant_id is null or p_restaurant_location_id is null or p_restaurant_menu_id is null or p_catalog_product_id is null then
        raise exception 'An exact PriceTrace identity is required.' using errcode = '23514';
    end if;
    select food.* into v_food from public.nutrition_foods food
    where food.id = p_nutrition_food_id and food.owner_id = v_user_id and food.kind = 'external_menu'
      and food.source_type in ('manual_estimate', 'food_image_estimate') and food.deleted_at is null for update;
    if not found then raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002'; end if;
    begin v_identity := coalesce(nullif(btrim(v_food.source_reference), '')::jsonb, '{}'::jsonb);
    exception when others then v_identity := '{}'::jsonb; end;
    v_identity := v_identity || jsonb_build_object('schema_version', 'dining-out-identity.v1', 'namespace', 'pricetrace',
        'restaurant_id', p_restaurant_id, 'restaurant_location_id', p_restaurant_location_id,
        'restaurant_menu_id', p_restaurant_menu_id, 'catalog_product_id', p_catalog_product_id,
        'restaurant_name', v_food.brand, 'menu_name', v_food.name);
    update public.nutrition_foods food set source_reference = v_identity::text, updated_at = v_updated_at
    where food.id = p_nutrition_food_id returning food.id, food.source_reference, food.updated_at
    into nutrition_food_id, source_reference, updated_at;
    return next;
end;
$$;

create or replace function public.attach_dining_out_menu_nutrition_link_v1(
    p_nutrition_food_id text, p_catalog_product_id uuid
)
returns table (link_id uuid, nutrition_food_id text, catalog_product_id uuid, status text, revision integer)
language plpgsql security definer set search_path = ''
as $$
declare
    v_user_id text := (select auth.uid())::text;
    v_food public.nutrition_foods%rowtype;
    v_identity jsonb;
    v_now timestamptz := now();
    v_existing_id uuid;
    v_existing_deleted_at timestamptz;
begin
    if v_user_id is null then raise exception 'Authentication is required.' using errcode = '42501'; end if;
    if p_catalog_product_id is null then raise exception 'An exact catalog_product_id is required.' using errcode = '23514'; end if;
    select food.* into v_food from public.nutrition_foods food
    where food.id = p_nutrition_food_id and food.owner_id = v_user_id and food.kind = 'external_menu'
      and food.source_type in ('manual_estimate', 'food_image_estimate') and food.deleted_at is null for update;
    if not found then raise exception 'The owner dining-out menu was not found.' using errcode = 'P0002'; end if;
    begin v_identity := coalesce(nullif(btrim(v_food.source_reference), '')::jsonb, '{}'::jsonb);
    exception when others then v_identity := '{}'::jsonb; end;
    if coalesce(v_identity ->> 'schema_version', '') <> 'dining-out-identity.v1'
       or coalesce(v_identity ->> 'namespace', '') <> 'pricetrace'
       or coalesce(v_identity ->> 'catalog_product_id', '') <> p_catalog_product_id::text then
        raise exception 'The exact PriceTrace dining-out identity is required.' using errcode = '23514';
    end if;
    select link.id, link.deleted_at into v_existing_id, v_existing_deleted_at
    from public.product_nutrition_links link where link.owner_id = v_user_id
      and link.nutrition_food_id = p_nutrition_food_id and link.catalog_product_id = p_catalog_product_id
      and link.status = 'approved' order by link.created_at desc limit 1;
    if v_existing_id is not null and v_existing_deleted_at is null then
        return query select link.id, link.nutrition_food_id, link.catalog_product_id, link.status, link.revision
        from public.product_nutrition_links link where link.id = v_existing_id;
        return;
    end if;
    update public.product_nutrition_links link set deleted_at = v_now, updated_at = v_now
    where link.owner_id = v_user_id and link.nutrition_food_id = p_nutrition_food_id
      and link.status = 'approved' and link.deleted_at is null and link.catalog_product_id <> p_catalog_product_id;
    if v_existing_id is not null then
        update public.product_nutrition_links link set deleted_at = null, reviewed_at = v_now, updated_at = v_now
        where link.id = v_existing_id returning link.id, link.nutrition_food_id, link.catalog_product_id, link.status, link.revision
        into link_id, nutrition_food_id, catalog_product_id, status, revision;
        return next; return;
    end if;
    return query insert into public.product_nutrition_links (owner_id, nutrition_food_id, catalog_product_id, status,
        source_type, proposal_reference, product_contract_version, revision, reviewed_at, created_at, updated_at, deleted_at)
    values (v_user_id, p_nutrition_food_id, p_catalog_product_id, 'approved', 'manual_selection',
        'FitnessApp dining-out publication', 'product-read.v1', 1, v_now, v_now, v_now, null)
    returning id, nutrition_food_id, catalog_product_id, status, revision;
end;
$$;

revoke all on function public.set_dining_out_menu_publication_v1(text, boolean),
    public.attach_dining_out_menu_identity_v1(text, uuid, uuid, uuid, uuid),
    public.attach_dining_out_menu_nutrition_link_v1(text, uuid) from public, anon;
grant execute on function public.set_dining_out_menu_publication_v1(text, boolean),
    public.attach_dining_out_menu_identity_v1(text, uuid, uuid, uuid, uuid),
    public.attach_dining_out_menu_nutrition_link_v1(text, uuid) to authenticated;

comment on function public.import_canonical_nutrition_v2(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) is 'Authenticated canonical Nutrition input boundary. Separates nutrition-label.v1 observed facts from food-estimate.v1 estimates, retains per-nutrient evidence, and writes only a private point-value nutrition_foods projection.';
comment on table public.nutrition_canonical_imports is 'Canonical OCR/App Nutrition provenance, separate from the legacy-compatible nutrition_foods projection.';
comment on table public.nutrition_food_nutrient_provenance is 'Per-nutrient observed or estimated provenance for a canonical Nutrition import.';
