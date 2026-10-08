do $$
declare
    v_versions text[];
    v_metadata_count integer;
    v_event_count integer;
begin
    select array_agg(m.version::text order by m.version)
    into v_versions
    from supabase_migrations.schema_migrations as m;

    if cardinality(v_versions) <> 29 then
        raise exception 'Historical replay expected 29 recovered migrations, got %', cardinality(v_versions);
    end if;
    if not ('20260814065823' = any(v_versions)) then
        raise exception 'Historical Kaguri migration was not recorded';
    end if;
    if not ('20260919120000' = any(v_versions))
       or not ('20260922152608' = any(v_versions))
       or not ('20260924132818' = any(v_versions)) then
        raise exception 'Recovered Nutrition migration history is incomplete';
    end if;

    select count(*)
    into v_metadata_count
    from public.product_nutrition_links as link
    join public.nutrition_foods as food
      on food.id = link.nutrition_food_id
    where food.id = 'historical-replay-kaguri-food'
      and food.owner_id = 'historical-replay-owner'
      and food.visibility = 'public'
      and food.publication_revision = 3
      and food.published_by = 'historical-replay-owner'
      and link.status = 'approved'
      and link.catalog_product_revision =
          'sha256:4a0e24d2150802a2a85d228f35104e56ec5ff75c3366f1e17414f63963a6864b'
      and link.catalog_content_amount = 103
      and link.catalog_content_unit = 'g'
      and link.catalog_package_count = 1;

    if v_metadata_count <> 1 then
        raise exception 'Synthetic Kaguri prerequisite did not receive the exact historical repair';
    end if;

    select count(*)
    into v_event_count
    from public.nutrition_food_publication_events as event
    where event.nutrition_food_id = 'historical-replay-kaguri-food'
      and event.owner_id = 'historical-replay-owner';

    if v_event_count <> 2
       or not exists (
           select 1 from public.nutrition_food_publication_events as event
           where event.nutrition_food_id = 'historical-replay-kaguri-food'
             and event.action = 'unpublish'
       )
       or not exists (
           select 1 from public.nutrition_food_publication_events as event
           where event.nutrition_food_id = 'historical-replay-kaguri-food'
             and event.action = 'publish'
       ) then
        raise exception 'Historical Kaguri repair did not preserve its audited publication transition';
    end if;

    if to_regclass('public.nutrition_ocr_dining_out_publications') is not null
       or to_regprocedure('public.publish_verified_ocr_dining_out_nutrition_v1(text,uuid,text,uuid,uuid,uuid,uuid)') is not null then
        raise exception 'A pending OCR publication migration ran during the 29-migration historical replay';
    end if;
end;
$$;

select 'PASS 29-migration historical replay and synthetic Kaguri repair' as result;
