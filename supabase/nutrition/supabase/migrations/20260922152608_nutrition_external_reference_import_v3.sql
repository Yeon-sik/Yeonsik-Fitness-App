-- Additive Nutrition v3 external-reference contract support.
-- Existing migrations remain unchanged. These constraints and the replacement
-- function extend the already-applied canonical v2/v3 contract in place.

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_input_contract_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_input_contract_check
        check (input_contract in ('nutrition-label.v1', 'food-estimate.v1', 'external-reference.v1'));

alter table public.nutrition_canonical_imports
    drop constraint if exists nutrition_canonical_imports_projection_source_type_check;
alter table public.nutrition_canonical_imports
    add constraint nutrition_canonical_imports_projection_source_type_check
        check (projection_source_type in ('product_label_ocr', 'external_reference', 'food_image_estimate'));

alter table public.nutrition_food_nutrient_provenance
    drop constraint if exists nutrition_food_nutrient_provenance_source_type_check;
alter table public.nutrition_food_nutrient_provenance
    add constraint nutrition_food_nutrient_provenance_source_type_check
        check (source_type in ('product_label_ocr', 'external_reference', 'food_image_estimate', 'menu_reference', 'manual'));

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
    v_external_source_reference text;
    v_external_source_version text;
    v_external_parser_version text;
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
    if v_contract not in ('nutrition-label.v1', 'food-estimate.v1', 'external-reference.v1') then
        raise exception 'input_contract must be nutrition-label.v1, food-estimate.v1, or external-reference.v1.'
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

    if v_contract = 'external-reference.v1' then
        v_external_source_reference := v_provenance ->> 'source_reference';
        v_external_source_version := v_provenance ->> 'source_version';
        v_external_parser_version := v_provenance ->> 'parser_version';

        if v_external_source_reference is null
           or v_external_source_reference <> btrim(v_external_source_reference)
           or v_external_source_reference !~* '^https?://[^[:space:]/]+([:/][^[:space:]]*)?$'
           or v_external_source_reference ~* '^https?://[^[:space:]/]*@'
           or v_external_source_reference ~* '^https?://(localhost|[^[:space:]/]+\.localhost|[^[:space:]/]+\.local|0\.0\.0\.0|127(\.[0-9]{1,3}){3}|10(\.[0-9]{1,3}){3}|169\.254(\.[0-9]{1,3}){2}|192\.168(\.[0-9]{1,3}){2}|172\.(1[6-9]|2[0-9]|3[01])(\.[0-9]{1,3}){2}|::1)([:/]|$)' then
            raise exception 'external-reference.v1 requires a public http/https source_reference.'
                using errcode = '23514';
        end if;
        if v_source_document_ref <> v_external_source_reference then
            raise exception 'external-reference.v1 source_document_ref must equal provenance source_reference.'
                using errcode = '23514';
        end if;
        if coalesce(lower(btrim(v_provenance ->> 'source_type')), '') <> 'external_reference'
           or coalesce(btrim(v_external_source_version), '') <> 'external-nutrition-lookup.v1'
           or coalesce(btrim(v_external_parser_version), '') <> 'external-nutrition-lookup.v1'
           or coalesce(btrim(v_provenance ->> 'canonical_input_contract'), '') <> 'external-reference.v1'
           or coalesce(lower(btrim(v_provenance ->> 'estimated')), '') <> 'false' then
            raise exception 'external-reference.v1 provenance must identify the non-estimated external nutrition lookup.'
                using errcode = '23514';
        end if;
        if p_estimation_evidence is not null and p_estimation_evidence <> 'null'::jsonb then
            raise exception 'external-reference.v1 cannot contain estimation evidence.'
                using errcode = '23514';
        end if;
        if p_pricetrace_identity is not null and p_pricetrace_identity <> 'null'::jsonb then
            raise exception 'external-reference.v1 cannot promote a client-supplied PriceTrace identity.'
                using errcode = '23514';
        end if;
    end if;

    -- The seven v2 required keys remain exact: missing and extra keys are both rejected.
    if (select count(*) from jsonb_object_keys(v_required)) <> 7
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
    if (select count(*) from jsonb_object_keys(v_nutrient_provenance)) <> 7
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
           or v_nutrient_source_type not in ('product_label_ocr', 'external_reference', 'food_image_estimate', 'menu_reference', 'manual') then
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
        if v_contract = 'external-reference.v1'
           and (
               v_value_status <> 'observed'
               or v_nutrient_source_type <> 'external_reference'
               or not (v_item -> 'evidence_refs' @> jsonb_build_array(v_external_source_reference))
           ) then
            raise exception 'external-reference.v1 accepts only observed external_reference nutrient values backed by its public source_reference.'
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

    if v_contract in ('nutrition-label.v1', 'external-reference.v1') then
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
    elsif v_contract = 'external-reference.v1' then
        v_projection_source_type := 'external_reference';
        v_legacy_evidence_type := 'product_label';
        v_legacy_source_type := 'product_label';
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
                v_user_id || ':' || btrim(p_idempotency_key) || ':' || v_hierarchy_fingerprint
                    || case when v_contract = 'external-reference.v1'
                            then ':external-reference.v1' else '' end,
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
        basis_amount = case when v_contract = 'external-reference.v1'
                            then p_basis_amount else basis_amount end,
        basis_unit = case when v_contract = 'external-reference.v1'
                          then btrim(p_basis_unit) else basis_unit end,
        source_type = v_projection_source_type,
        source_reference = case when v_contract = 'external-reference.v1'
                                then v_external_source_reference else source_reference end,
        source_version = case when v_contract = 'external-reference.v1'
                              then v_external_source_version else 'nutrition-projection.v3' end,
        visibility = 'private',
        updated_at = now()
    where id = v_projection.nutrition_food_id
      and owner_id = v_user_id
      and deleted_at is null;

    if v_contract = 'external-reference.v1' then
        -- The compatibility executor writes a product-label projection first;
        -- restore the producer's external provenance on its audit row as well.
        update public.nutrition_verified_imports
        set provenance = v_provenance,
            updated_at = now()
        where id = v_projection.import_id
          and owner_id = v_user_id;
    end if;

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
    'Authenticated canonical Nutrition v3 import. Preserves legacy nutrient/provenance validation, accepts nutrition-label.v1, food-estimate.v1, and external-reference.v1, preserves public external source provenance, stores explicit packaged-product hierarchy, rejects hierarchy on restaurant estimates, and fingerprints hierarchy for idempotency.';
