-- Packaged-product identity is an explicit hierarchy. The legacy brand column
-- remains the compatibility/display value; these columns retain the source
-- facts without deriving missing levels from one another.
alter table public.nutrition_foods
    add column if not exists manufacturer_name text,
    add column if not exists brand_name text,
    add column if not exists sub_brand_name text,
    add column if not exists product_name text;

create index if not exists nutrition_foods_owner_product_hierarchy_idx
    on public.nutrition_foods (
        owner_id,
        manufacturer_name,
        brand_name,
        sub_brand_name,
        product_name
    );

-- Hierarchy edits are content edits, so the existing nutrition-read revision
-- must advance when one of the new source facts changes.
create or replace function public.bump_nutrition_food_revision()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if row(
        new.name,
        new.brand,
        new.manufacturer_name,
        new.brand_name,
        new.sub_brand_name,
        new.product_name,
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
        old.brand,
        old.manufacturer_name,
        old.brand_name,
        old.sub_brand_name,
        old.product_name,
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

comment on column public.nutrition_foods.manufacturer_name is
    'Explicit packaged-product manufacturer source fact; NULL means it was not supplied.';
comment on column public.nutrition_foods.brand_name is
    'Explicit packaged-product brand source fact; legacy brand remains the compatibility alias.';
comment on column public.nutrition_foods.sub_brand_name is
    'Explicit packaged-product sub-brand source fact; NULL means it was not supplied.';
comment on column public.nutrition_foods.product_name is
    'Explicit packaged-product product-name source fact; NULL means it was not supplied.';

drop function if exists public.get_nutrition_read_v3(text);
create function public.get_nutrition_read_v3(p_query text default null)
returns table (
    contract_version text,
    nutrition_food_id text,
    brand text,
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
    catalog_product_id uuid,
    standard_product_id uuid,
    manufacturer_name text,
    brand_name text,
    sub_brand_name text,
    product_name text
)
language sql
stable
security invoker
set search_path = ''
as $$
    select
        'nutrition-read.v3'::text,
        food.id,
        food.brand,
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
        approved.catalog_product_id,
        approved.standard_product_id,
        food.manufacturer_name,
        food.brand_name,
        food.sub_brand_name,
        food.product_name
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
        select link.catalog_product_id, link.standard_product_id
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
          or coalesce(food.manufacturer_name, '') ilike '%' || btrim(p_query) || '%'
          or coalesce(food.brand_name, food.brand, '') ilike '%' || btrim(p_query) || '%'
          or coalesce(food.sub_brand_name, '') ilike '%' || btrim(p_query) || '%'
          or coalesce(food.product_name, food.name, '') ilike '%' || btrim(p_query) || '%'
          or food.name ilike '%' || btrim(p_query) || '%'
      )
    order by
        coalesce(food.manufacturer_name, ''),
        coalesce(food.brand_name, food.brand, ''),
        coalesce(food.sub_brand_name, ''),
        coalesce(food.product_name, food.name, ''),
        food.id;
$$;

revoke all on function public.get_nutrition_read_v3(text) from public;
grant execute on function public.get_nutrition_read_v3(text) to anon, authenticated;

comment on function public.get_nutrition_read_v3(text) is
    'nutrition-read.v3: nutrition-read.v2 fields plus explicit packaged-product hierarchy facts.';

-- v3 retains the v2 nutrient and provenance contracts and adds only the four
-- explicit packaged-product hierarchy inputs. The legacy p_brand parameter is
-- retained as an input alias for older callers; it is never used to invent a
-- missing hierarchy level.
create or replace function public.import_canonical_nutrition_v3(
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
    p_estimation_evidence jsonb default null,
    p_manufacturer_name text default null,
    p_brand_name text default null,
    p_sub_brand_name text default null,
    p_product_name text default null
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
    visibility text,
    manufacturer_name text,
    brand_name text,
    sub_brand_name text,
    product_name text
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
    v_input_brand text;
    v_manufacturer_name text;
    v_brand_name text;
    v_sub_brand_name text;
    v_product_name text;
    v_legacy_brand text;
    v_hierarchy_fingerprint text;
    v_projection_source_type text;
    v_legacy_evidence_type text;
    v_legacy_source_type text;
    v_legacy_provenance jsonb;
    v_request_payload jsonb;
    v_existing public.nutrition_canonical_imports%rowtype;
    v_canonical_import_id uuid := gen_random_uuid();
    v_projection record;
    v_legacy_idempotency_key text;
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
        raise exception 'Only user-verified Nutrition values may be imported.'
            using errcode = '42514';
    end if;
    if v_contract not in ('nutrition-label.v1', 'food-estimate.v1') then
        raise exception 'input_contract must be nutrition-label.v1 or food-estimate.v1.'
            using errcode = '22023';
    end if;
    if length(btrim(coalesce(p_idempotency_key, ''))) not between 1 and 200
       or length(v_source_document_ref) not between 1 and 1000 then
        raise exception 'Idempotency key and source document reference must be within their contract limits.'
            using errcode = '22023';
    end if;
    if jsonb_typeof(v_required) <> 'object'
       or jsonb_typeof(v_optional) <> 'object'
       or jsonb_typeof(v_nutrient_provenance) <> 'object'
       or jsonb_typeof(v_provenance) <> 'object' then
        raise exception 'Nutrients and provenance must be objects.' using errcode = '22023';
    end if;

    -- The seven v2 required keys remain exact: missing and extra keys are both rejected.
    if jsonb_object_length(v_required) <> 7
       or exists (
           select 1
           from jsonb_object_keys(v_required) as required_key(key_name)
           where key_name not in (
               'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
               'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
           )
       ) then
        raise exception 'required_nutrients must contain exactly the seven required nutrient keys.'
            using errcode = '23514';
    end if;
    if jsonb_object_length(v_nutrient_provenance) <> 7
       or exists (
           select 1
           from jsonb_object_keys(v_nutrient_provenance) as provenance_key(key_name)
           where key_name not in (
               'calories_kcal', 'carbs_grams', 'protein_grams', 'fat_grams',
               'sugars_grams', 'saturated_fat_grams', 'sodium_mg'
           )
       ) then
        raise exception 'nutrient_provenance must contain exactly the seven required nutrient keys.'
            using errcode = '23514';
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
           or (v_item ->> 'value')::numeric < 0
           or jsonb_typeof(v_item -> 'evidence_refs') <> 'array'
           or jsonb_array_length(v_item -> 'evidence_refs') = 0 then
            raise exception 'Every required nutrient needs a matching non-negative value and at least one evidence reference.'
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

    -- Normalize only the supplied hierarchy facts. NULL/blank never falls back to
    -- p_food_name, p_brand, or a PriceTrace value.
    v_input_brand := nullif(btrim(coalesce(p_brand, '')), '');
    v_manufacturer_name := nullif(btrim(coalesce(p_manufacturer_name, '')), '');
    v_brand_name := nullif(btrim(coalesce(p_brand_name, '')), '');
    v_sub_brand_name := nullif(btrim(coalesce(p_sub_brand_name, '')), '');
    v_product_name := nullif(btrim(coalesce(p_product_name, '')), '');

    if v_input_brand is not null
       and v_brand_name is not null
       and v_input_brand <> v_brand_name then
        raise exception 'p_brand must match p_brand_name when both are supplied.'
            using errcode = '23514';
    end if;

    if v_contract = 'nutrition-label.v1' then
        v_legacy_brand := coalesce(v_brand_name, v_input_brand);
    else
        if v_manufacturer_name is not null
           or v_brand_name is not null
           or v_sub_brand_name is not null
           or v_product_name is not null then
            raise exception 'Packaged-product hierarchy is not valid for restaurant nutrition.'
                using errcode = '22023';
        end if;
        v_legacy_brand := v_input_brand;
    end if;

    v_hierarchy_fingerprint := encode(
        extensions.digest(
            convert_to(
                jsonb_build_array(
                    v_manufacturer_name,
                    v_brand_name,
                    v_sub_brand_name,
                    v_product_name
                )::text,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );

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
           or jsonb_typeof(p_estimation_evidence) <> 'object'
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
        'contract_version', 'fitness-nutrition-canonical-import.v3',
        'idempotency_key', btrim(p_idempotency_key),
        'input_contract', v_contract,
        'source_document_ref', v_source_document_ref,
        'food_name', btrim(coalesce(p_food_name, '')),
        'brand', v_legacy_brand,
        'category', p_category,
        'basis_amount', p_basis_amount,
        'basis_unit', p_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'nutrient_provenance', v_nutrient_provenance,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', coalesce(p_pricetrace_identity, 'null'::jsonb),
        'estimation_evidence', coalesce(p_estimation_evidence, 'null'::jsonb),
        'manufacturer_name', v_manufacturer_name,
        'brand_name', v_brand_name,
        'sub_brand_name', v_sub_brand_name,
        'product_name', v_product_name,
        'hierarchy_fingerprint', v_hierarchy_fingerprint
    );

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':canonical-nutrition-v3:' || btrim(p_idempotency_key), 0)
    );
    select * into v_existing
    from public.nutrition_canonical_imports
    where owner_id = v_user_id and idempotency_key = btrim(p_idempotency_key)
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different canonical payload.' using errcode = '23505';
        end if;
        return query select
            v_existing.id,
            true,
            v_existing.nutrition_food_id,
            v_existing.input_contract,
            v_existing.projection_source_type,
            v_existing.projection_import_id,
            case when v_existing.pricetrace_identity is null then null
                 else (v_existing.pricetrace_identity ->> 'catalog_product_id')::uuid end,
            (select estimation_evidence_id
             from public.nutrition_verified_imports
             where id = v_existing.projection_import_id),
            'private',
            v_existing.request_payload ->> 'manufacturer_name',
            v_existing.request_payload ->> 'brand_name',
            v_existing.request_payload ->> 'sub_brand_name',
            v_existing.request_payload ->> 'product_name';
        return;
    end if;

    -- Keep the legacy projection executor, but namespace its internal key with
    -- the v3 hierarchy fingerprint so v1/v2/v3 retries cannot alias silently.
    v_legacy_idempotency_key := 'canonical-v3:' || encode(
        extensions.digest(
            convert_to(
                v_user_id || ':' || btrim(p_idempotency_key) || ':' || v_hierarchy_fingerprint,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );
    select * into v_projection
    from public.import_verified_nutrition_v1(
        v_legacy_idempotency_key,
        v_source_document_ref,
        v_legacy_evidence_type,
        btrim(coalesce(p_food_name, '')),
        v_legacy_brand,
        p_category,
        p_basis_amount,
        p_basis_unit,
        v_required,
        v_optional,
        v_legacy_provenance,
        true,
        p_pricetrace_identity,
        p_estimation_evidence
    );

    update public.nutrition_foods
    set brand = v_legacy_brand,
        manufacturer_name = v_manufacturer_name,
        brand_name = v_brand_name,
        sub_brand_name = v_sub_brand_name,
        product_name = v_product_name,
        source_type = v_projection_source_type,
        source_version = 'nutrition-projection.v3',
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
        v_provenance,
        case when p_pricetrace_identity is null or p_pricetrace_identity = 'null'::jsonb
             then null else p_pricetrace_identity end,
        v_request_payload, v_projection.import_id,
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
        v_canonical_import_id,
        false,
        v_projection.nutrition_food_id,
        v_contract,
        v_projection_source_type,
        v_projection.import_id,
        v_projection.catalog_product_id,
        v_projection.estimation_evidence_id,
        'private',
        v_manufacturer_name,
        v_brand_name,
        v_sub_brand_name,
        v_product_name;
end;
$$;

revoke all on function public.import_canonical_nutrition_v3(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) from public, anon;
grant execute on function public.import_canonical_nutrition_v3(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) to authenticated;

comment on function public.import_canonical_nutrition_v3(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) is
    'Authenticated canonical Nutrition v3 import. Preserves v2 nutrient/provenance validation, stores explicit packaged-product hierarchy, rejects hierarchy on restaurant estimates, and fingerprints hierarchy for idempotency.';
