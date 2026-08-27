-- Harden the canonical Nutrition import boundary without changing either RPC contract.
-- Manual catalog rows remain writable by their owner. OCR/external projection rows can
-- only be created or changed by the SECURITY DEFINER import functions, which also write
-- the provenance records.

alter table public.nutrition_foods enable row level security;
alter table public.nutrition_verified_imports enable row level security;
alter table public.nutrition_verified_catalog_keys enable row level security;
alter table public.nutrition_estimation_evidence enable row level security;
alter table public.nutrition_canonical_imports enable row level security;
alter table public.nutrition_food_nutrient_provenance enable row level security;

-- Reassert the Data API boundary explicitly. The import functions are SECURITY DEFINER;
-- authenticated clients only receive the owner-scoped read surface below.
revoke all on public.nutrition_verified_imports,
    public.nutrition_verified_catalog_keys,
    public.nutrition_estimation_evidence,
    public.nutrition_canonical_imports,
    public.nutrition_food_nutrient_provenance
    from public, anon, authenticated;
grant select on public.nutrition_verified_imports,
    public.nutrition_verified_catalog_keys,
    public.nutrition_estimation_evidence,
    public.nutrition_canonical_imports,
    public.nutrition_food_nutrient_provenance
    to authenticated;

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

drop policy if exists nutrition_canonical_imports_select
    on public.nutrition_canonical_imports;
create policy nutrition_canonical_imports_select
    on public.nutrition_canonical_imports
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

drop policy if exists nutrition_food_nutrient_provenance_select
    on public.nutrition_food_nutrient_provenance;
create policy nutrition_food_nutrient_provenance_select
    on public.nutrition_food_nutrient_provenance
    for select
    to authenticated
    using (owner_id = ((select auth.uid())::text));

-- nutrition_foods is still the compatibility projection and still supports the existing
-- manual-input UI. Trusted import source types are deliberately absent from these direct
-- client policies. A legacy restaurant projection that has a v1 audit row is protected too.
revoke all on public.nutrition_foods from public, anon, authenticated;
grant select on public.nutrition_foods to anon, authenticated;
grant insert, update, delete on public.nutrition_foods to authenticated;

drop policy if exists nutrition_foods_select on public.nutrition_foods;
create policy nutrition_foods_select
    on public.nutrition_foods
    for select
    to anon, authenticated
    using (
        deleted_at is null
        and (visibility = 'public' or owner_id = ((select auth.uid())::text))
    );

drop policy if exists nutrition_foods_insert on public.nutrition_foods;
create policy nutrition_foods_insert
    on public.nutrition_foods
    for insert
    to authenticated
    with check (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
        and (
            lower(btrim(coalesce(source_type, ''))) in (
                'manual', 'manual_option', 'manual_recipe',
                'pricetrace_manual', 'pricetrace_standard'
            )
            or (
                lower(btrim(coalesce(source_type, ''))) = 'manual_estimate'
                and not exists (
                    select 1
                    from public.nutrition_verified_imports verified
                    where verified.owner_id = ((select auth.uid())::text)
                      and verified.nutrition_food_id = nutrition_foods.id
                )
            )
        )
    );

drop policy if exists nutrition_foods_update on public.nutrition_foods;
create policy nutrition_foods_update
    on public.nutrition_foods
    for update
    to authenticated
    using (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
        and (
            lower(btrim(coalesce(source_type, ''))) in (
                'manual', 'manual_option', 'manual_recipe',
                'pricetrace_manual', 'pricetrace_standard'
            )
            or (
                lower(btrim(coalesce(source_type, ''))) = 'manual_estimate'
                and not exists (
                    select 1
                    from public.nutrition_verified_imports verified
                    where verified.owner_id = ((select auth.uid())::text)
                      and verified.nutrition_food_id = nutrition_foods.id
                )
            )
        )
    )
    with check (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
        and (
            lower(btrim(coalesce(source_type, ''))) in (
                'manual', 'manual_option', 'manual_recipe',
                'pricetrace_manual', 'pricetrace_standard'
            )
            or lower(btrim(coalesce(source_type, ''))) = 'manual_estimate'
        )
    );

drop policy if exists nutrition_foods_delete on public.nutrition_foods;
create policy nutrition_foods_delete
    on public.nutrition_foods
    for delete
    to authenticated
    using (
        owner_id = ((select auth.uid())::text)
        and visibility = 'private'
        and (
            lower(btrim(coalesce(source_type, ''))) in (
                'manual', 'manual_option', 'manual_recipe',
                'pricetrace_manual', 'pricetrace_standard'
            )
            or (
                lower(btrim(coalesce(source_type, ''))) = 'manual_estimate'
                and not exists (
                    select 1
                    from public.nutrition_verified_imports verified
                    where verified.owner_id = ((select auth.uid())::text)
                      and verified.nutrition_food_id = nutrition_foods.id
                )
            )
        )
    );

-- Keep the v1 and v2 RPC names/signatures stable while making their privileges explicit.
revoke all on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) from public, anon;
grant execute on function public.import_verified_nutrition_v1(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    boolean, jsonb, jsonb
) to authenticated;

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
    v_legacy_idempotency_key text;
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

    -- The seven required keys are an exact contract, not a best-effort subset. This
    -- rejects both missing and extra keys before the legacy projection is invoked.
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

    -- v1 and v2 deliberately have independent idempotency namespaces. A digest keeps the
    -- internal key below v1's 200-character limit even when the public v2 key is maximal.
    v_legacy_idempotency_key := 'canonical-v2:' || encode(
        extensions.digest(
            convert_to(v_user_id || ':' || btrim(p_idempotency_key), 'UTF8'),
            'sha256'
        ),
        'hex'
    );
    select * into v_projection
    from public.import_verified_nutrition_v1(
        v_legacy_idempotency_key, v_source_document_ref, v_legacy_evidence_type,
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

comment on function public.import_canonical_nutrition_v2(
    text, text, text, text, text, text, numeric, text, jsonb, jsonb, jsonb,
    jsonb, boolean, jsonb, jsonb
) is
    'Authenticated canonical Nutrition import boundary. Requires the exact seven required nutrient/provenance keys, stores owner-scoped provenance, and projects through a namespaced legacy v1 idempotency key.';
