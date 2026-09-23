-- Additive canonical Nutrition contract for OCR-App V2 text nutrition lookup.
-- The existing v3 implementation is retained under a private legacy name and
-- remains the executor for nutrition-label.v1 and food-estimate.v1.

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_input_contract_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_input_contract_check
    check (input_contract in ('nutrition-label.v1', 'food-estimate.v1', 'external-reference.v1'));

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_projection_source_type_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_projection_source_type_check
    check (projection_source_type in ('product_label_ocr', 'food_image_estimate', 'external_reference'));

alter table public.nutrition_food_nutrient_provenance
    drop constraint if exists nutrition_food_nutrient_provenance_source_type_check;
alter table public.nutrition_food_nutrient_provenance
    add constraint nutrition_food_nutrient_provenance_source_type_check
    check (source_type in (
        'product_label_ocr', 'external_reference', 'food_image_estimate', 'menu_reference', 'manual'
    ));

alter table public.nutrition_verified_imports
    drop constraint if exists nutrition_verified_imports_evidence_type_check;
alter table public.nutrition_verified_imports
    add constraint nutrition_verified_imports_evidence_type_check
    check (evidence_type in ('product_label', 'restaurant_estimate', 'external_reference'));

create or replace function public.import_external_reference_nutrition_v1(
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
    v_idempotency_key text := btrim(coalesce(p_idempotency_key, ''));
    v_source_reference text := btrim(coalesce(p_source_document_ref, ''));
    v_source_authority text;
    v_source_host text;
    v_food_name text := btrim(coalesce(p_food_name, ''));
    v_input_brand text := nullif(btrim(coalesce(p_brand, '')), '');
    v_manufacturer_name text := nullif(btrim(coalesce(p_manufacturer_name, '')), '');
    v_brand_name text := nullif(btrim(coalesce(p_brand_name, '')), '');
    v_sub_brand_name text := nullif(btrim(coalesce(p_sub_brand_name, '')), '');
    v_product_name text := nullif(btrim(coalesce(p_product_name, '')), '');
    v_legacy_brand text;
    v_category text := lower(btrim(coalesce(p_category, '')));
    v_basis_unit text := lower(btrim(coalesce(p_basis_unit, '')));
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_nutrient_provenance jsonb := coalesce(p_nutrient_provenance, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_request_payload jsonb;
    v_projection_request_payload jsonb;
    v_existing public.nutrition_canonical_imports%rowtype;
    v_canonical_import_id uuid := gen_random_uuid();
    v_projection_import_id uuid := gen_random_uuid();
    v_food_id text;
    v_catalog_key text;
    v_projection_idempotency_key text;
    v_hierarchy_fingerprint text;
    v_key text;
    v_item jsonb;
    v_value_status text;
    v_nutrient_source_type text;
begin
    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.' using errcode = '42514';
    end if;
    if v_contract <> 'external-reference.v1' then
        raise exception 'input_contract must be external-reference.v1.' using errcode = '22023';
    end if;
    if length(v_idempotency_key) not between 1 and 200 then
        raise exception 'Idempotency key must be within its contract limits.' using errcode = '22023';
    end if;
    if p_source_document_ref is null
       or p_source_document_ref <> v_source_reference
       or length(v_source_reference) not between 1 and 1000 then
        raise exception 'external-reference.v1 requires an untrimmed source URL.' using errcode = '22023';
    end if;
    if v_source_reference !~* '^https?://[^[:space:]/?#@]+(:[0-9]+)?([/?#][^[:space:]]*)?$' then
        raise exception 'external-reference.v1 source_reference must be a public http/https URL.'
            using errcode = '22023';
    end if;
    v_source_authority := split_part(split_part(split_part(v_source_reference, '://', 2), '/', 1), '?', 1);
    v_source_authority := split_part(v_source_authority, '#', 1);
    if left(v_source_authority, 1) = '[' then
        v_source_host := lower(split_part(v_source_authority, ']', 1));
    else
        v_source_host := lower(regexp_replace(v_source_authority, ':[0-9]+$', ''));
    end if;
    v_source_host := btrim(v_source_host, '[]');
    if v_source_host is null or length(v_source_host) = 0
       or v_source_host = 'localhost'
       or v_source_host like '%.localhost'
       or v_source_host like '%.local'
       or v_source_host in ('::1', '0.0.0.0', '127.0.0.1')
       or v_source_host ~* '^(fc|fd)[0-9a-f]{2}:'
       or v_source_host ~* '^fe[89ab][0-9a-f]:'
       or v_source_host ~* '^::ffff:(127\.|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)'
       or v_source_host ~ '^(0|10|127)\.'
       or v_source_host ~ '^169\.254\.'
       or v_source_host ~ '^192\.168\.'
       or v_source_host ~ '^172\.(1[6-9]|2[0-9]|3[0-1])\.' then
        raise exception 'external-reference.v1 source_reference must use a public URL host.'
            using errcode = '22023';
    end if;
    if v_food_name = '' or v_category = '' or p_basis_amount is null or p_basis_amount <= 0
       or v_basis_unit = '' then
        raise exception 'Food name, category, and a positive Nutrition basis are required.'
            using errcode = '22023';
    end if;
    if v_category not in ('meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable', 'fruit',
                         'legume', 'dairy', 'nut_seed', 'processed', 'beverage', 'recipe', 'other') then
        raise exception 'category is not supported by the Nutrition catalog.' using errcode = '22023';
    end if;
    if v_basis_unit not in ('g', 'mg', 'kg', 'ml', 'l', 'serving', 'portion', 'pack', 'each', '개') then
        raise exception 'basis_unit is not supported by the Nutrition catalog.' using errcode = '22023';
    end if;
    if jsonb_typeof(v_required) <> 'object'
       or jsonb_typeof(v_optional) <> 'object'
       or jsonb_typeof(v_nutrient_provenance) <> 'object'
       or jsonb_typeof(v_provenance) <> 'object' then
        raise exception 'Nutrients and provenance must be objects.' using errcode = '22023';
    end if;
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
           or (v_item ->> 'value')::numeric < 0 then
            raise exception 'Every required nutrient needs a matching non-negative value.'
                using errcode = '23514', detail = v_key;
        end if;
        if jsonb_typeof(v_item -> 'evidence_refs') <> 'array'
           or jsonb_array_length(v_item -> 'evidence_refs') = 0 then
            raise exception 'Every external nutrient needs at least one evidence reference.'
                using errcode = '23514', detail = v_key;
        end if;
        v_value_status := lower(btrim(coalesce(v_item ->> 'value_status', '')));
        v_nutrient_source_type := lower(btrim(coalesce(v_item ->> 'source_type', '')));
        if v_value_status <> 'observed' or v_nutrient_source_type <> 'external_reference' then
            raise exception 'external-reference.v1 requires observed external_reference nutrient provenance.'
                using errcode = '23514', detail = v_key;
        end if;
        if not exists (
            select 1
            from jsonb_array_elements_text(v_item -> 'evidence_refs') as evidence_ref(value)
            where evidence_ref.value = v_source_reference
        ) then
            raise exception 'Each external nutrient must retain the public source URL in evidence_refs.'
                using errcode = '23514', detail = v_key;
        end if;
    end loop;

    foreach v_key in array ARRAY[
        'fiber_grams', 'added_sugars_grams', 'trans_fat_grams', 'cholesterol_mg'
    ] loop
        if v_optional ? v_key
           and (jsonb_typeof(v_optional -> v_key) <> 'number'
                or (v_optional ->> v_key)::numeric < 0) then
            raise exception 'Optional Nutrition values must be non-negative numbers.'
                using errcode = '22023', detail = v_key;
        end if;
    end loop;
    if coalesce(v_provenance ->> 'source_type', '') <> 'external_reference'
       or coalesce(v_provenance ->> 'source_reference', '') <> v_source_reference
       or coalesce(v_provenance ->> 'parser_version', '') <> 'external-nutrition-lookup.v1'
       or coalesce(v_provenance ->> 'source_version', '') <> 'external-nutrition-lookup.v1'
       or coalesce(v_provenance ->> 'canonical_input_contract', '') <> 'external-reference.v1' then
        raise exception 'external-reference.v1 provenance must retain source URL and external-nutrition-lookup.v1.'
            using errcode = '23514';
    end if;
    if coalesce(v_provenance ->> 'estimated', '') <> 'false' then
        raise exception 'external-reference.v1 cannot be estimated.' using errcode = '23514';
    end if;
    if p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
        raise exception 'external-reference.v1 cannot contain estimation evidence.' using errcode = '22023';
    end if;
    if p_pricetrace_identity is not null and p_pricetrace_identity <> 'null'::jsonb then
        raise exception 'external-reference.v1 cannot accept PriceTrace identity as client authority.'
            using errcode = '22023';
    end if;

    if v_input_brand is not null
       and v_brand_name is not null
       and v_input_brand <> v_brand_name then
        raise exception 'p_brand must match p_brand_name when both are supplied.'
            using errcode = '23514';
    end if;
    v_legacy_brand := coalesce(v_brand_name, v_input_brand);
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
    v_request_payload := jsonb_build_object(
        'contract_version', 'fitness-nutrition-canonical-import.v3',
        'idempotency_key', v_idempotency_key,
        'input_contract', v_contract,
        'source_document_ref', v_source_reference,
        'food_name', v_food_name,
        'brand', v_legacy_brand,
        'category', v_category,
        'basis_amount', p_basis_amount,
        'basis_unit', v_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'nutrient_provenance', v_nutrient_provenance,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', 'null'::jsonb,
        'estimation_evidence', 'null'::jsonb,
        'manufacturer_name', v_manufacturer_name,
        'brand_name', v_brand_name,
        'sub_brand_name', v_sub_brand_name,
        'product_name', v_product_name,
        'hierarchy_fingerprint', v_hierarchy_fingerprint
    );

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':canonical-nutrition-v3:' || v_idempotency_key, 0)
    );
    select * into v_existing
    from public.nutrition_canonical_imports
    where owner_id = v_user_id and idempotency_key = v_idempotency_key
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
            null::uuid,
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

    v_catalog_key := 'external-reference:' || encode(
        extensions.digest(
            convert_to(
                v_user_id || ':' || v_source_reference || ':' || v_food_name || ':' ||
                    coalesce(v_legacy_brand, '') || ':' || v_category || ':' ||
                    p_basis_amount::text || ':' || v_basis_unit,
                'UTF8'
            ),
            'sha256'
        ),
        'hex'
    );
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
            id, owner_id, name, brand, manufacturer_name, brand_name, sub_brand_name, product_name,
            kind, category, basis_amount, basis_unit, prep_state,
            calories_kcal, protein_grams, carbs_grams, fat_grams, sodium_mg,
            saturated_fat_grams, sugars_grams, fiber_grams, added_sugars_grams,
            trans_fat_grams, cholesterol_mg, source_type, source_reference, source_version,
            data_version, visibility, created_at, updated_at, deleted_at
        ) values (
            v_food_id, v_user_id, v_food_name, v_legacy_brand,
            v_manufacturer_name, v_brand_name, v_sub_brand_name, v_product_name,
            'external_menu', v_category, p_basis_amount, v_basis_unit, 'unspecified',
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
            'external_reference', v_source_reference, 'external-nutrition-lookup.v1',
            2, 'private', now(), now(), null
        );
        insert into public.nutrition_verified_catalog_keys (
            owner_id, catalog_key, nutrition_food_id
        ) values (v_user_id, v_catalog_key, v_food_id);
    else
        update public.nutrition_foods
        set name = v_food_name,
            brand = v_legacy_brand,
            manufacturer_name = v_manufacturer_name,
            brand_name = v_brand_name,
            sub_brand_name = v_sub_brand_name,
            product_name = v_product_name,
            category = v_category,
            basis_amount = p_basis_amount,
            basis_unit = v_basis_unit,
            prep_state = 'unspecified',
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
            source_type = 'external_reference',
            source_reference = v_source_reference,
            source_version = 'external-nutrition-lookup.v1',
            data_version = 2,
            visibility = 'private',
            updated_at = now()
        where id = v_food_id and owner_id = v_user_id and deleted_at is null;
        if not found then
            raise exception 'The Nutrition catalog mapping points to an unavailable food.'
                using errcode = 'P0002';
        end if;
    end if;

    v_projection_idempotency_key := 'canonical-v3-external:' || encode(
        extensions.digest(convert_to(v_user_id || ':' || v_idempotency_key, 'UTF8'), 'sha256'),
        'hex'
    );
    v_projection_request_payload := jsonb_build_object(
        'contract_version', 'fitness-nutrition-verified-import.v1',
        'idempotency_key', v_projection_idempotency_key,
        'source_document_ref', v_source_reference,
        'evidence_type', 'external_reference',
        'food_name', v_food_name,
        'brand', v_legacy_brand,
        'category', v_category,
        'basis_amount', p_basis_amount,
        'basis_unit', v_basis_unit,
        'required_nutrients', v_required,
        'optional_nutrients', v_optional,
        'provenance', v_provenance,
        'user_verified', true,
        'pricetrace_identity', 'null'::jsonb,
        'estimation_evidence', 'null'::jsonb,
        'source_type', 'external_reference',
        'source_version', 'external-nutrition-lookup.v1'
    );
    insert into public.nutrition_verified_imports (
        id, owner_id, contract_version, idempotency_key, source_document_ref,
        evidence_type, food_name, brand, category, basis_amount, basis_unit,
        required_nutrients, optional_nutrients, provenance, user_verified,
        pricetrace_identity, catalog_key, request_payload, nutrition_food_id,
        estimation_evidence_id
    ) values (
        v_projection_import_id, v_user_id, 'fitness-nutrition-verified-import.v1',
        v_projection_idempotency_key, v_source_reference, 'external_reference',
        v_food_name, v_legacy_brand, v_category, p_basis_amount, v_basis_unit,
        v_required, v_optional, v_provenance, true, null, v_catalog_key,
        v_projection_request_payload, v_food_id, null
    );

    insert into public.nutrition_canonical_imports (
        id, owner_id, input_contract, idempotency_key, source_document_ref,
        user_verified, required_nutrients, optional_nutrients, nutrient_provenance,
        provenance, pricetrace_identity, request_payload, projection_import_id,
        nutrition_food_id, projection_source_type
    ) values (
        v_canonical_import_id, v_user_id, v_contract, v_idempotency_key,
        v_source_reference, true, v_required, v_optional, v_nutrient_provenance,
        v_provenance, null, v_request_payload, v_projection_import_id,
        v_food_id, 'external_reference'
    );

    insert into public.nutrition_food_nutrient_provenance (
        owner_id, nutrition_food_id, canonical_import_id, nutrient_code, value,
        value_status, source_type, evidence_refs, confidence, uncertainty_range
    )
    select
        v_user_id,
        v_food_id,
        v_canonical_import_id,
        key,
        (value ->> 'value')::numeric,
        value ->> 'value_status',
        value ->> 'source_type',
        value -> 'evidence_refs',
        null,
        null
    from jsonb_each(v_nutrient_provenance);

    return query select
        v_canonical_import_id,
        false,
        v_food_id,
        v_contract,
        'external_reference',
        v_projection_import_id,
        null::uuid,
        null::uuid,
        'private',
        v_manufacturer_name,
        v_brand_name,
        v_sub_brand_name,
        v_product_name;
end;
$$;

revoke all on function public.import_external_reference_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) from public, anon, authenticated;

-- Keep the already deployed legacy v3 body intact and route only the new contract
-- through the external-reference executor above.
alter function public.import_canonical_nutrition_v3(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) rename to import_canonical_nutrition_v3_legacy;

revoke all on function public.import_canonical_nutrition_v3_legacy(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) from public, anon, authenticated;

create function public.import_canonical_nutrition_v3(
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
begin
    if lower(btrim(coalesce(p_input_contract, ''))) = 'external-reference.v1' then
        return query
        select *
        from public.import_external_reference_nutrition_v1(
            p_idempotency_key, p_input_contract, p_source_document_ref, p_food_name,
            p_brand, p_category, p_basis_amount, p_basis_unit, p_required_nutrients,
            p_nutrient_provenance, p_optional_nutrients, p_provenance, p_user_verified,
            p_pricetrace_identity, p_estimation_evidence, p_manufacturer_name,
            p_brand_name, p_sub_brand_name, p_product_name
        );
    else
        return query
        select *
        from public.import_canonical_nutrition_v3_legacy(
            p_idempotency_key, p_input_contract, p_source_document_ref, p_food_name,
            p_brand, p_category, p_basis_amount, p_basis_unit, p_required_nutrients,
            p_nutrient_provenance, p_optional_nutrients, p_provenance, p_user_verified,
            p_pricetrace_identity, p_estimation_evidence, p_manufacturer_name,
            p_brand_name, p_sub_brand_name, p_product_name
        );
    end if;
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
    'Authenticated canonical Nutrition v3 import. Legacy nutrition-label.v1 and food-estimate.v1 behavior is delegated unchanged; external-reference.v1 stores published external nutrition provenance without product-label or PriceTrace authority substitution.';

comment on function public.import_external_reference_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) is
    'OCR-App V2 text nutrition lookup boundary. Requires a public source URL, external-nutrition-lookup.v1 provenance, seven observed nutrients, and authenticated user verification.';
