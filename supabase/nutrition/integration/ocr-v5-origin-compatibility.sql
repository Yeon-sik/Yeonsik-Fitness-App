-- Local/disposable only. V5 origin is audit metadata; these are existing Fitness
-- payloads, never an OCR V5 parser. All writes roll back.
begin;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-4000-8000-000000000081","role":"authenticated"}', true);
set local role authenticated;

do $test$
declare
    v_values jsonb := '{"calories_kcal":450,"protein_grams":22,"carbs_grams":51,"fat_grams":15,"sodium_mg":650,"saturated_fat_grams":5,"sugars_grams":8}';
    v_evidence jsonb;
    v_observed jsonb;
    v_provenance jsonb := '{"schema_version":"yeonsik-ocr.v5","source_app":"ocr-app","artifact_key":"nutrition:food-photo","estimated":true}';
    v_source jsonb := '{"schema_version":"yeonsik-ocr.v5","projection":"FITNESS_MEAL","source_app":"ocr-app","meal_kind":"dining_out","verified_consumption":true}';
    v_result record;
    v_replay record;
    v_publication record;
    v_meal record;
    v_items jsonb;
    v_import_id uuid;
    v_food_id text;
    v_pending_food text;
    v_rpc text;
    v_version integer;
    v_restaurant uuid := '00000000-0000-4000-8000-000000000091';
    v_location uuid := '00000000-0000-4000-8000-000000000092';
    v_menu uuid := '00000000-0000-4000-8000-000000000093';
    v_catalog uuid := '00000000-0000-4000-8000-000000000094';
begin
    select jsonb_object_agg(key, jsonb_build_object('value', value,
        'value_status', 'estimated', 'source_type', 'food_image_estimate',
        'evidence_refs', jsonb_build_array('ocr-app://v5/food-photo/region/food')))
        into v_evidence from jsonb_each(v_values);
    select jsonb_object_agg(key, jsonb_build_object('value', value,
        'value_status', 'observed', 'source_type', 'product_label_ocr',
        'evidence_refs', jsonb_build_array('ocr-app://legacy/label/nutrients')))
        into v_observed from jsonb_each(v_values);
    for v_version in 2..3 loop
        v_rpc := 'public.import_canonical_nutrition_v' || v_version;
        execute format('select * from %s(p_idempotency_key=>$1,p_input_contract=>$2,
            p_source_document_ref=>$3,p_food_name=>$4,p_brand=>$5,p_category=>$6,
            p_basis_amount=>1,p_basis_unit=>''serving'',p_required_nutrients=>$7,
            p_nutrient_provenance=>$8,p_provenance=>$9,p_user_verified=>true,
            p_estimation_evidence=>''{"confidence":0.8}''::jsonb)', v_rpc)
            into v_result using 'v5-estimate-v' || v_version, 'food-estimate.v1',
                'ocr-app://v5/food-photo/' || v_version, 'V5 Fixture Menu v' || v_version,
                'V5 Fixture Restaurant v' || v_version, 'recipe', v_values, v_evidence, v_provenance;
        v_import_id := v_result.canonical_import_id;
        v_food_id := v_result.nutrition_food_id;
        if v_result.idempotent_replay or v_food_id is null then raise exception 'New estimate import failed'; end if;
        if not exists(select 1 from public.nutrition_foods where id=v_food_id
            and visibility='private' and source_type='food_image_estimate' and data_version=2
            and manufacturer_name is null and brand_name is null and sub_brand_name is null and product_name is null)
            then raise exception 'Private-first estimate/hierarchy projection changed'; end if;
        if (select count(*) from public.nutrition_food_nutrient_provenance where canonical_import_id=v_import_id) <> 7
            then raise exception 'Seven provenance rows required'; end if;
        if not exists(select 1 from public.nutrition_canonical_imports where id=v_import_id
            and provenance->>'schema_version'='yeonsik-ocr.v5') then raise exception 'V5 origin lost'; end if;
        if exists(select 1 from public.meal_records) then raise exception 'Nutrition inferred consumption'; end if;
        if exists(select 1 from public.nutrition_canonical_imports where id=v_import_id
            and (request_payload::text like '%purchase_date%' or request_payload::text like '%payment_status%'
                or request_payload::text like '%price_krw%')) then raise exception 'Purchase financial facts leaked'; end if;

        -- Unresolved and ambiguous metadata never supply authority IDs.
        foreach v_pending_food in array array['unresolved', 'needs_review'] loop
            begin
                perform public.publish_verified_ocr_dining_out_nutrition_v1(
                    'v5-block-' || v_version || '-' || v_pending_food, v_import_id, v_food_id,
                    null, null, null, null);
                raise exception 'Missing PT authority was published';
            exception when check_violation then null; end;
        end loop;
        execute 'reset role'; -- Trusted fixture inspection of server-only audit tables.
        if not exists(select 1 from public.nutrition_foods where id=v_food_id and visibility='private')
            or exists(select 1 from public.nutrition_ocr_dining_out_publications where nutrition_food_id=v_food_id)
            then raise exception 'Publication fail-closed changed source'; end if;
        execute 'set local role authenticated';

        select * into v_publication from public.publish_verified_ocr_dining_out_nutrition_v1(
            'v5-publication-' || v_version, v_import_id, v_food_id,
            v_restaurant, v_location, v_menu, v_catalog);
        if v_publication.visibility <> 'public' or v_publication.replayed
            or v_publication.restaurant_menu_id <> v_menu then raise exception 'Exact PT publication failed'; end if;
        select * into v_replay from public.publish_verified_ocr_dining_out_nutrition_v1(
            'v5-publication-' || v_version, v_import_id, v_food_id,
            v_restaurant, v_location, v_menu, v_catalog);
        if not v_replay.replayed or v_replay.nutrition_link_id <> v_publication.nutrition_link_id
            then raise exception 'Publication replay duplicated result'; end if;
        execute 'reset role';
        if (select count(*) from public.product_nutrition_links where nutrition_food_id=v_food_id and status='approved') <> 1
            or (select count(*) from public.nutrition_dining_out_publication_events where nutrition_food_id=v_food_id) <> 1
            then raise exception 'Duplicate publication/link'; end if;
        execute 'set local role authenticated';
        begin
            perform public.publish_verified_ocr_dining_out_nutrition_v1(
                'v5-publication-' || v_version, v_import_id, v_food_id,
                v_restaurant, v_location, v_menu, '00000000-0000-4000-8000-000000000095');
            raise exception 'Changed publication replay accepted';
        exception when unique_violation then null; end;
        begin
        execute format('select * from %s(p_idempotency_key=>$1,p_input_contract=>$2,
            p_source_document_ref=>$3,p_food_name=>$4,p_brand=>$5,p_category=>$6,
            p_basis_amount=>1,p_basis_unit=>''serving'',p_required_nutrients=>$7,
            p_nutrient_provenance=>$8,p_provenance=>$9,p_user_verified=>true,
            p_estimation_evidence=>''{"confidence":0.8}''::jsonb)', v_rpc)
            into v_replay using 'v5-estimate-v' || v_version, 'food-estimate.v1',
                'ocr-app://v5/food-photo/' || v_version, 'V5 Fixture Menu v' || v_version,
                'V5 Fixture Restaurant v' || v_version, 'recipe', v_values, v_evidence, v_provenance;
        if not v_replay.idempotent_replay or v_replay.nutrition_food_id <> v_food_id
            then raise exception 'Nutrition replay did not return original'; end if;

        exception when ambiguous_column then
            if v_version <> 2 or position('estimation_evidence_id' in SQLERRM) = 0 then raise; end if;
            raise notice 'BASELINE_V2_ESTIMATE_REPLAY_FAILURE_REPRODUCED';
        end;

        -- Existing V2/V3 label route and V1/V2/V3 audit origins remain accepted.
        execute format('select * from %s(p_idempotency_key=>$1,p_input_contract=>''nutrition-label.v1'',
            p_source_document_ref=>''ocr-app://legacy/label'',p_food_name=>''Legacy Fixture'',
            p_brand=>null,p_category=>''processed'',p_basis_amount=>100,p_basis_unit=>''g'',
            p_required_nutrients=>$2,p_nutrient_provenance=>$3,p_provenance=>$4,p_user_verified=>true)', v_rpc)
            into v_result using 'legacy-label-v' || v_version, v_values, v_observed,
                jsonb_build_object('schema_version', 'yeonsik-ocr.v' || v_version, 'estimated',false);
        if v_result.nutrition_food_id is null then raise exception 'Existing label regression'; end if;
    end loop;

    -- Nutrition-only and purchase-time-only imports create no Meals.
    if exists(select 1 from public.meal_records) then raise exception 'No-consumption created Meal'; end if;
    v_items := jsonb_build_array(jsonb_build_object('client_key','food-photo-line',
        'nutrition_food_id',v_food_id,'amount',0.5,'unit','serving','confidence',0.9,
        'source_provenance',jsonb_build_object('schema_version','yeonsik-ocr.v5',
            'artifact_key','nutrition:food-photo','verified_consumption',true)));
    begin
        perform public.import_verified_meal_v1('v5-purchase-time-only',null,v_items,
            '{"schema_version":"yeonsik-ocr.v5","source_app":"ocr-app","purchase_at":"2026-09-05T18:00:00+09:00"}');
        raise exception 'Purchase timestamp substituted for consumed_at';
    exception when invalid_parameter_value then null; end;
    if exists(select 1 from public.meal_records) then raise exception 'Missing consumption created Meal'; end if;
    select * into v_meal from public.import_verified_meal_v1('v5-consumption',
        '2026-09-05T19:20:00+09:00',v_items,v_source);
    if v_meal.idempotent_replay or v_meal.item_count <> 1 or v_meal.record_date <> '2026-09-05'::date
        then raise exception 'Explicit consumption failed'; end if;
    select * into v_replay from public.import_verified_meal_v1('v5-consumption',
        '2026-09-05T19:20:00+09:00',v_items,v_source);
    if not v_replay.idempotent_replay or v_replay.meal_record_id <> v_meal.meal_record_id
        or (select count(*) from public.meal_records) <> 1
        or (select count(*) from public.meal_record_items) <> 1 then raise exception 'Meal replay duplicate'; end if;
    if not exists(select 1 from public.meal_record_items where meal_record_id=v_meal.meal_record_id
        and consumed_amount=0.5 and source_provenance->>'schema_version'='yeonsik-ocr.v5')
        then raise exception 'Consumption/source provenance lost'; end if;
    begin
        perform public.import_verified_meal_v1('v5-consumption','2026-09-05T18:00:00+09:00',v_items,v_source);
        raise exception 'Changed consumed time replay accepted';
    exception when unique_violation then null; end;

    -- Mandatory 7 nutrients and numeric confidence remain fail-closed.
    begin
        perform public.import_canonical_nutrition_v3('v5-missing-nutrient','food-estimate.v1',
            'ocr-app://v5/invalid','Invalid',null,'recipe',1,'serving',v_values-'sodium_mg',
            v_evidence,p_provenance=>v_provenance,p_user_verified=>true,
            p_estimation_evidence=>'{"confidence":0.8}');
        raise exception 'Missing nutrient accepted';
    exception when check_violation then null; end;
    begin
        perform public.import_canonical_nutrition_v3('v5-no-confidence','food-estimate.v1',
            'ocr-app://v5/invalid','Invalid',null,'recipe',1,'serving',v_values,
            v_evidence,p_provenance=>v_provenance,p_user_verified=>true);
        raise exception 'Missing confidence accepted';
    exception when check_violation then null; end;

    perform set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-000000000082","role":"authenticated"}',true);
    begin
        perform public.publish_verified_ocr_dining_out_nutrition_v1('foreign-v5',v_import_id,v_food_id,
            v_restaurant,v_location,v_menu,v_catalog);
        raise exception 'Foreign owner published';
    exception when no_data_found then null; end;
    perform set_config('request.jwt.claims','{}',true);
    begin
        perform public.import_verified_meal_v1('anonymous-v5','2026-09-05T19:20:00+09:00',v_items,v_source);
        raise exception 'Anonymous Meal accepted';
    exception when insufficient_privilege then null; end;
end;
$test$;
reset role;
select 'FITNESS_V5_V3_COMPATIBILITY_PASS_WITH_KNOWN_V2_REPLAY_DEFECT' as result;
rollback;
