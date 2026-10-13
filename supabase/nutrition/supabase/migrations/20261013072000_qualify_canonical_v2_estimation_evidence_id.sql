-- Repair only the canonical v2 idempotent replay evidence lookup.
-- Preserve historical migrations, the exact RPC contract, and existing grants.
do $qualify_canonical_v2_estimation_evidence_id$
declare
    v_target regprocedure;
    v_original text;
    v_repaired text;
    v_after text;
    v_anchor text := '(select estimation_evidence_id from public.nutrition_verified_imports where id = v_existing.projection_import_id)';
    v_qualified text := '(select verified_import.estimation_evidence_id from public.nutrition_verified_imports as verified_import where verified_import.id = v_existing.projection_import_id)';
    v_anchor_count integer;
begin
    v_target := pg_catalog.to_regprocedure(
        'public.import_canonical_nutrition_v2(text,text,text,text,text,text,numeric,text,jsonb,jsonb,jsonb,jsonb,boolean,jsonb,jsonb)'
    );
    if v_target is null then
        raise exception 'Expected exact public.import_canonical_nutrition_v2 signature was not found';
    end if;

    v_original := pg_catalog.pg_get_functiondef(v_target::oid);
    v_anchor_count := (
        pg_catalog.length(v_original)
        - pg_catalog.length(pg_catalog.replace(v_original, v_anchor, ''))
    ) / pg_catalog.length(v_anchor);
    if v_anchor_count <> 1 then
        raise exception 'Expected exactly one ambiguous estimation_evidence_id lookup in public.import_canonical_nutrition_v2; found %',
            v_anchor_count;
    end if;

    v_repaired := pg_catalog.replace(v_original, v_anchor, v_qualified);
    if v_repaired = v_original then
        raise exception 'The exact canonical v2 idempotent replay evidence lookup was not changed';
    end if;

    execute v_repaired;

    v_after := pg_catalog.pg_get_functiondef(v_target::oid);
    if v_after is distinct from v_repaired
       or pg_catalog.strpos(v_after, v_anchor) <> 0
       or pg_catalog.strpos(v_after, v_qualified) = 0 then
        raise exception 'Qualified canonical v2 idempotent replay evidence lookup did not persist exactly';
    end if;
end;
$qualify_canonical_v2_estimation_evidence_id$;
