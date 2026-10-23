-- Actual PostgreSQL contract assertions shared by native replay and PGlite.
do $brand_replay$ begin
        if (select count(*) from brand_catalog_replay.legacy_reads) <> 3 or exists(
          select 1 from brand_catalog_replay.legacy_reads where definition <> pg_get_functiondef(oid)
        ) then raise exception 'Brand migration changed an existing Nutrition read contract'; end if;
        if exists(
          (select to_jsonb(v) from public.get_nutrition_read_v3(null) v
           except all select to_jsonb(v) - array['pricetrace_brand_id','pricetrace_brand_name_snapshot',
             'pricetrace_parent_brand_id_snapshot','pricetrace_parent_brand_name_snapshot']
           from public.get_nutrition_read_v4(null) v)
          union all
          (select to_jsonb(v) - array['pricetrace_brand_id','pricetrace_brand_name_snapshot',
             'pricetrace_parent_brand_id_snapshot','pricetrace_parent_brand_name_snapshot']
           from public.get_nutrition_read_v4(null) v
           except all select to_jsonb(v) from public.get_nutrition_read_v3(null) v)
        ) then raise exception 'Nutrition v4 common projection differs from v3'; end if;
      end $brand_replay$;
