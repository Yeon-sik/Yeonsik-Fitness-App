-- This diagnostic is temporary while verifying the second error surfaced after
-- qualifying the recorded nutrition_food_id ambiguity. It runs only against the
-- disposable local replay database and rolls all writes back.
begin;
set local role authenticated;

do $reproduce_generic_v3_followup_ambiguity$
declare
    v_owner_id uuid := gen_random_uuid();
    v_catalog_product_id uuid := gen_random_uuid();
    v_required jsonb := jsonb_build_object(
        'calories_kcal', 240,
        'carbs_grams', 44,
        'protein_grams', 27,
        'fat_grams', 12,
        'sugars_grams', 9,
        'saturated_fat_grams', 3,
        'sodium_mg', 420
    );
    v_nutrient_provenance jsonb;
    v_result record;
    v_message text;
    v_context text;
begin
    perform set_config('request.jwt.claim.sub', v_owner_id::text, true);
    perform set_config(
        'request.jwt.claims',
        jsonb_build_object('sub', v_owner_id::text, 'role', 'authenticated')::text,
        true
    );

    select jsonb_object_agg(
        nutrient.key,
        jsonb_build_object(
            'value', nutrient.value,
            'value_status', 'observed',
            'source_type', 'product_label_ocr',
            'evidence_refs', jsonb_build_array(
                'https://example.test/replay/' || nutrient.key
            )
        )
    )
    into v_nutrient_provenance
    from jsonb_each(v_required) as nutrient(key, value);

    begin
        select *
        into v_result
        from public.import_canonical_nutrition_v3(
            'followup-ambiguity-' || v_owner_id::text,
            'nutrition-label.v1',
            'https://example.test/replay/nutrition-label-followup',
            'Synthetic Follow-up Ambiguity Label',
            'Synthetic Brand',
            'processed',
            100,
            'g',
            v_required,
            v_nutrient_provenance,
            '{}'::jsonb,
            '{}'::jsonb,
            true,
            jsonb_build_object(
                'namespace', 'pricetrace',
                'catalog_product_id', v_catalog_product_id
            ),
            null,
            'Synthetic Manufacturer',
            'Synthetic Brand',
            'Synthetic Sub-brand',
            'Synthetic Product'
        );

        raise exception 'Expected to capture the follow-up generic v3 ambiguity.';
    exception
        when sqlstate '42702' then
            get stacked diagnostics
                v_message = message_text,
                v_context = pg_exception_context;

            if v_message <> 'column reference "estimation_evidence_id" is ambiguous' then
                raise exception 'Unexpected follow-up generic v3 ambiguity: %; context=%',
                    v_message, coalesce(v_context, '<none>');
            end if;

            raise notice 'PASS follow-up generic v3 SQLSTATE 42702; message=%; context=%',
                v_message,
                replace(coalesce(v_context, '<none>'), E'\n', ' | ');
    end;
end;
$reproduce_generic_v3_followup_ambiguity$;

rollback;
