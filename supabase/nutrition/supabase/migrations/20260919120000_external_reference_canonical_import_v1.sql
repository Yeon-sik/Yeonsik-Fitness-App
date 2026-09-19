-- Additive canonical input for public, packaged-product Nutrition references.
-- Existing nutrition-label.v1 and food-estimate.v1 callers delegate to the
-- already-applied implementations unchanged.

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_input_contract_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_input_contract_check
    check (input_contract in (
        'nutrition-label.v1', 'food-estimate.v1', 'external-reference.v1'
    ));

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_projection_source_type_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_projection_source_type_check
    check (projection_source_type in (
        'product_label_ocr', 'food_image_estimate', 'external_reference'
    ));

alter table public.nutrition_food_nutrient_provenance
    drop constraint if exists nutrition_food_nutrient_provenance_source_type_check;
alter table public.nutrition_food_nutrient_provenance
    add constraint nutrition_food_nutrient_provenance_source_type_check
    check (source_type in (
        'product_label_ocr', 'food_image_estimate', 'menu_reference', 'manual',
        'external_reference'
    ));

-- The legacy projection audit is private to the canonical boundary after this
-- migration. Its table keeps the new projection's source facts without making
-- external-reference.v1 masquerade as product_label evidence.
alter table public.nutrition_verified_imports
    drop constraint if exists nutrition_verified_imports_evidence_type_check;
alter table public.nutrition_verified_imports
    add constraint nutrition_verified_imports_evidence_type_check
    check (evidence_type in ('product_label', 'restaurant_estimate', 'external_reference'));

alter function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) rename to import_verified_nutrition_v1_legacy;

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
    v_evidence_type text := lower(btrim(coalesce(p_evidence_type, '')));
    v_user_id text := (select auth.uid())::text;
    v_source_document_ref text := btrim(coalesce(p_source_document_ref, ''));
    v_food_name text := btrim(coalesce(p_food_name, ''));
    v_brand text := nullif(btrim(coalesce(p_brand, '')), '');
    v_category text := lower(btrim(coalesce(p_category, '')));
    v_basis_unit text := lower(btrim(coalesce(p_basis_unit, '')));
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_identity jsonb := case
        when p_pricetrace_identity is null or p_pricetrace_identity = 'null'::jsonb
            then null
        else p_pricetrace_identity
    end;
    v_request_payload jsonb;
    v_existing public.nutrition_verified_imports%rowtype;
    v_food_id text;
    v_import_id uuid := gen_random_uuid();
    v_catalog_product_id uuid;
    v_catalog_key text;
    v_key text;
    v_number numeric;
    v_existing_link public.product_nutrition_links%rowtype;
    v_link_created boolean := false;
begin
    if v_evidence_type <> 'external_reference' then
        return query select * from public.import_verified_nutrition_v1_legacy(
            p_idempotency_key, p_source_document_ref, p_evidence_type,
            p_food_name, p_brand, p_category, p_basis_amount, p_basis_unit,
            p_required_nutrients, p_optional_nutrients, p_provenance,
            p_user_verified, p_pricetrace_identity, p_estimation_evidence
        );
        return;
    end if;

    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.' using errcode = '42514';
    end if;
    if length(btrim(coalesce(p_idempotency_key, ''))) not between 1 and 200
       or length(v_source_document_ref) not between 1 and 1000
       or length(v_food_name) = 0
       or length(v_category) = 0
       or p_basis_amount is null or p_basis_amount <= 0
       or length(v_basis_unit) = 0 then
        raise exception 'External reference import has invalid identity or Nutrition basis.'
            using errcode = '22023';
    end if;
    if v_category not in ('meat', 'poultry', 'seafood', 'egg', 'grain', 'vegetable',
                          'fruit', 'legume', 'dairy', 'nut_seed', 'processed',
                          'beverage', 'recipe', 'other') then
        raise exception 'category is not supported by the Nutrition catalog.' using errcode = '22023';
    end if;
    if v_basis_unit not in ('g', 'mg', 'kg', 'ml', 'l', 'serving', 'portion', 'pack', 'each', '개') then
        raise exception 'basis_unit is not supported by the Nutrition catalog.' using errcode = '22023';
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
           or jsonb_typeof(v_required -> v_key) <> 'number'
           or (v_required ->> v_key)::numeric < 0 then
            raise exception 'All seven required Nutrition values are required and non-negative.'
                using errcode = '23514', detail = v_key;
        end if;
    end loop;
    if jsonb_object_length(v_required) <> 7 then
        raise exception 'external-reference.v1 requires exactly seven required nutrients.'
            using errcode = '23514';
    end if;
    if coalesce(v_provenance ->> 'source_type', '') <> 'external_reference'
       or coalesce(v_provenance ->> 'canonical_input_contract', '') <> 'external-reference.v1'
       or coalesce(v_provenance ->> 'estimated', 'false') <> 'false'
       or p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
        raise exception 'external-reference.v1 cannot contain estimation or another provenance source.'
            using errcode = '23514';
    end if;

    if v_identity is not null then
        if jsonb_typeof(v_identity) <> 'object'
           or coalesce(v_identity ->> 'namespace', '') <> 'pricetrace'
           or nullif(v_identity ->> 'catalog_product_id', '') is null
           or v_identity ? 'restaurant_id'
           or v_identity ? 'restaurant_location_id'
           or v_identity ? 'restaurant_menu_id' then
            raise exception 'An external reference may carry only a resolved PriceTrace catalog_product_id.'
                using errcode = '23514';
        end if;
        begin
            v_catalog_product_id := (v_identity ->> 'catalog_product_id')::uuid;
        exception when others then
            raise exception 'catalog_product_id must be a UUID.' using errcode = '22023';
        end;
    end if;

    v_catalog_key := 'external_reference:' || case
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
        'estimation_evidence', 'null'::jsonb
    );

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(v_user_id || ':idempotency:' || btrim(p_idempotency_key), 0)
    );
    select * into v_existing
    from public.nutrition_verified_imports
    where owner_id = v_user_id and idempotency_key = btrim(p_idempotency_key)
    for update;
    if found then
        if v_existing.request_payload <> v_request_payload then
            raise exception 'The idempotency key was already used with a different payload.'
                using errcode = '23505';
        end if;
        return query select
            v_existing.id, true, v_existing.nutrition_food_id, v_existing.evidence_type,
            'external_reference', 'external_menu', 2, 'private', v_existing.catalog_key,
            case when v_existing.pricetrace_identity is null then null
                 else (v_existing.pricetrace_identity ->> 'catalog_product_id')::uuid end,
            v_existing.estimation_evidence_id, false;
        return;
    end if;

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
            v_food_id, v_user_id, v_food_name, v_brand, 'external_menu', v_category,
            p_basis_amount, v_basis_unit, 'unspecified',
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
            'external_reference',
            jsonb_build_object(
                'contract_version', 'fitness-nutrition-verified-import.v1',
                'evidence_type', 'external_reference',
                'source_document_ref', v_source_document_ref,
                'user_verified', true,
                'provenance', v_provenance,
                'pricetrace_identity', coalesce(v_identity, 'null'::jsonb)
            )::text,
            nullif(v_provenance ->> 'source_version', ''), 2, 'private', now(), now(), null
        );
        insert into public.nutrition_verified_catalog_keys (owner_id, catalog_key, nutrition_food_id)
        values (v_user_id, v_catalog_key, v_food_id);
    else
        update public.nutrition_foods
        set name = v_food_name,
            brand = v_brand,
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
            source_version = nullif(v_provenance ->> 'source_version', ''),
            data_version = 2, visibility = 'private', updated_at = now()
        where id = v_food_id and owner_id = v_user_id and deleted_at is null;
        if not found then
            raise exception 'The Nutrition catalog mapping points to an unavailable food.'
                using errcode = 'P0002';
        end if;
    end if;

    insert into public.nutrition_verified_imports (
        id, owner_id, contract_version, idempotency_key, source_document_ref,
        evidence_type, food_name, brand, category, basis_amount, basis_unit,
        required_nutrients, optional_nutrients, provenance, user_verified,
        pricetrace_identity, catalog_key, request_payload, nutrition_food_id,
        estimation_evidence_id
    ) values (
        v_import_id, v_user_id, 'fitness-nutrition-verified-import.v1',
        btrim(p_idempotency_key), v_source_document_ref, 'external_reference',
        v_food_name, v_brand, v_category, p_basis_amount, v_basis_unit,
        v_required, v_optional, v_provenance, true, v_identity, v_catalog_key,
        v_request_payload, v_food_id, null
    );

    if v_catalog_product_id is not null then
        select * into v_existing_link
        from public.product_nutrition_links
        where owner_id = v_user_id and nutrition_food_id = v_food_id
          and status = 'approved' and deleted_at is null
        order by created_at desc limit 1 for update;
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
                'manual_selection', 'fitness-nutrition-verified-import:' || v_import_id::text,
                'product-read.v1', 1, now(), now(), now(), null
            );
            v_link_created := true;
        end if;
    end if;

    return query select
        v_import_id, false, v_food_id, 'external_reference', 'external_reference',
        'external_menu', 2, 'private', v_catalog_key, v_catalog_product_id, null, v_link_created;
end;
$$;

revoke all on function public.import_verified_nutrition_v1_legacy(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) from public, anon, authenticated;
revoke all on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) from public, anon;
grant execute on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) to authenticated;

alter function public.import_canonical_nutrition_v3(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) rename to import_canonical_nutrition_v3_legacy;

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
    v_contract text := lower(btrim(coalesce(p_input_contract, '')));
    v_user_id text := (select auth.uid())::text;
    v_required jsonb := coalesce(p_required_nutrients, '{}'::jsonb);
    v_optional jsonb := coalesce(p_optional_nutrients, '{}'::jsonb);
    v_nutrient_provenance jsonb := coalesce(p_nutrient_provenance, '{}'::jsonb);
    v_provenance jsonb := coalesce(p_provenance, '{}'::jsonb);
    v_source_document_ref text := btrim(coalesce(p_source_document_ref, ''));
    v_input_brand text := nullif(btrim(coalesce(p_brand, '')), '');
    v_manufacturer_name text := nullif(btrim(coalesce(p_manufacturer_name, '')), '');
    v_brand_name text := nullif(btrim(coalesce(p_brand_name, '')), '');
    v_sub_brand_name text := nullif(btrim(coalesce(p_sub_brand_name, '')), '');
    v_product_name text := nullif(btrim(coalesce(p_product_name, '')), '');
    v_legacy_brand text;
    v_hierarchy_fingerprint text;
    v_request_payload jsonb;
    v_legacy_provenance jsonb;
    v_existing public.nutrition_canonical_imports%rowtype;
    v_canonical_import_id uuid := gen_random_uuid();
    v_projection record;
    v_legacy_idempotency_key text;
    v_key text;
    v_item jsonb;
begin
    if v_contract <> 'external-reference.v1' then
        return query select * from public.import_canonical_nutrition_v3_legacy(
            p_idempotency_key, p_input_contract, p_source_document_ref, p_food_name,
            p_brand, p_category, p_basis_amount, p_basis_unit, p_required_nutrients,
            p_nutrient_provenance, p_optional_nutrients, p_provenance,
            p_user_verified, p_pricetrace_identity, p_estimation_evidence,
            p_manufacturer_name, p_brand_name, p_sub_brand_name, p_product_name
        );
        return;
    end if;

    if v_user_id is null then
        raise exception 'Authentication is required.' using errcode = '42501';
    end if;
    if p_user_verified is not true then
        raise exception 'Only user-verified Nutrition values may be imported.' using errcode = '42514';
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
    if jsonb_object_length(v_required) <> 7
       or jsonb_object_length(v_nutrient_provenance) <> 7
       or exists (
           select 1 from jsonb_object_keys(v_required) as key_name(key_name)
           where key_name not in ('calories_kcal', 'carbs_grams', 'protein_grams',
               'fat_grams', 'sugars_grams', 'saturated_fat_grams', 'sodium_mg')
       )
       or exists (
           select 1 from jsonb_object_keys(v_nutrient_provenance) as key_name(key_name)
           where key_name not in ('calories_kcal', 'carbs_grams', 'protein_grams',
               'fat_grams', 'sugars_grams', 'saturated_fat_grams', 'sodium_mg')
       ) then
        raise exception 'external-reference.v1 requires exactly the seven required nutrient keys.'
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
           or lower(btrim(coalesce(v_item ->> 'value_status', ''))) <> 'observed'
           or lower(btrim(coalesce(v_item ->> 'source_type', ''))) in (
               'product_label_ocr', 'food_image_estimate', 'manual', 'menu_reference'
           )
           or lower(btrim(coalesce(v_item ->> 'source_type', ''))) <> 'external_reference'
           or jsonb_typeof(v_item -> 'evidence_refs') <> 'array'
           or jsonb_array_length(v_item -> 'evidence_refs') = 0
           or exists (
               select 1 from jsonb_array_elements(v_item -> 'evidence_refs') as ref(value)
               where jsonb_typeof(ref.value) <> 'string'
                  or (ref.value #>> '{}') !~* '^https?://[^[:space:]]+$'
           ) then
            raise exception 'external-reference.v1 requires observed external_reference values and public URL evidence.'
                using errcode = '23514', detail = v_key;
        end if;
    end loop;
    if coalesce(v_provenance ->> 'estimated', 'false') <> 'false'
       or p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
        raise exception 'external-reference.v1 cannot contain estimation evidence.' using errcode = '23514';
    end if;
    if v_input_brand is not null and v_brand_name is not null and v_input_brand <> v_brand_name then
        raise exception 'p_brand must match p_brand_name when both are supplied.' using errcode = '23514';
    end if;
    if p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
        raise exception 'external-reference.v1 cannot contain estimation evidence.' using errcode = '22023';
    end if;

    v_legacy_brand := coalesce(v_brand_name, v_input_brand);
    v_hierarchy_fingerprint := encode(
        extensions.digest(convert_to(jsonb_build_array(
            v_manufacturer_name, v_brand_name, v_sub_brand_name, v_product_name
        )::text, 'UTF8'), 'sha256'), 'hex'
    );
    v_legacy_provenance := v_provenance || jsonb_build_object(
        'source_type', 'external_reference',
        'canonical_input_contract', 'external-reference.v1',
        'estimated', false
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
        'estimation_evidence', 'null'::jsonb,
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
            raise exception 'The idempotency key was already used with a different canonical payload.'
                using errcode = '23505';
        end if;
        return query select v_existing.id, true, v_existing.nutrition_food_id,
            v_existing.input_contract, v_existing.projection_source_type,
            v_existing.projection_import_id,
            case when v_existing.pricetrace_identity is null then null
                 else (v_existing.pricetrace_identity ->> 'catalog_product_id')::uuid end,
            null::uuid, 'private',
            v_existing.request_payload ->> 'manufacturer_name',
            v_existing.request_payload ->> 'brand_name',
            v_existing.request_payload ->> 'sub_brand_name',
            v_existing.request_payload ->> 'product_name';
        return;
    end if;

    v_legacy_idempotency_key := 'canonical-v3:' || encode(extensions.digest(
        convert_to(v_user_id || ':' || btrim(p_idempotency_key) || ':' || v_hierarchy_fingerprint, 'UTF8'),
        'sha256'), 'hex');
    select * into v_projection
    from public.import_verified_nutrition_v1(
        v_legacy_idempotency_key, v_source_document_ref, 'external_reference',
        btrim(coalesce(p_food_name, '')), v_legacy_brand, p_category,
        p_basis_amount, p_basis_unit, v_required, v_optional,
        v_legacy_provenance, true, p_pricetrace_identity, null
    );

    update public.nutrition_foods
    set brand = v_legacy_brand,
        manufacturer_name = v_manufacturer_name,
        brand_name = v_brand_name,
        sub_brand_name = v_sub_brand_name,
        product_name = v_product_name,
        source_type = 'external_reference',
        source_version = 'nutrition-projection.v3',
        visibility = 'private', updated_at = now()
    where id = v_projection.nutrition_food_id
      and owner_id = v_user_id and deleted_at is null;

    insert into public.nutrition_canonical_imports (
        id, owner_id, input_contract, idempotency_key, source_document_ref,
        user_verified, required_nutrients, optional_nutrients, nutrient_provenance,
        provenance, pricetrace_identity, request_payload, projection_import_id,
        nutrition_food_id, projection_source_type
    ) values (
        v_canonical_import_id, v_user_id, v_contract, btrim(p_idempotency_key),
        v_source_document_ref, true, v_required, v_optional, v_nutrient_provenance,
        v_provenance, case when p_pricetrace_identity is null
            or p_pricetrace_identity = 'null'::jsonb then null else p_pricetrace_identity end,
        v_request_payload, v_projection.import_id, v_projection.nutrition_food_id,
        'external_reference'
    );

    insert into public.nutrition_food_nutrient_provenance (
        owner_id, nutrition_food_id, canonical_import_id, nutrient_code, value,
        value_status, source_type, evidence_refs, confidence, uncertainty_range
    )
    select v_user_id, v_projection.nutrition_food_id, v_canonical_import_id,
        key, (value ->> 'value')::numeric, value ->> 'value_status',
        value ->> 'source_type', value -> 'evidence_refs', null, null
    from jsonb_each(v_nutrient_provenance);

    return query select v_canonical_import_id, false, v_projection.nutrition_food_id,
        v_contract, 'external_reference', v_projection.import_id,
        v_projection.catalog_product_id, null::uuid, 'private',
        v_manufacturer_name, v_brand_name, v_sub_brand_name, v_product_name;
end;
$$;

revoke all on function public.import_canonical_nutrition_v3_legacy(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb, text, text, text, text
) from public, anon, authenticated;
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
    'Authenticated canonical Nutrition v3 import with nutrition-label.v1, food-estimate.v1, and external-reference.v1. External references retain public URL and external_reference provenance.';
