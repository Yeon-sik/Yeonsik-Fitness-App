-- Disposable replay only: preserve existing wire definitions before the Brand migration.
create schema brand_catalog_replay;
      create table brand_catalog_replay.legacy_reads as
      select p.oid, pg_get_functiondef(p.oid) as definition from pg_proc p
      join pg_namespace n on n.oid=p.pronamespace where n.nspname='public'
      and p.proname in ('get_nutrition_read_v1','get_nutrition_read_v2','get_nutrition_read_v3');
