import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Disposable PostgreSQL engine only. No network, hosted DB or production keys.
const runtime = process.argv[2];
if (!runtime) throw new Error('Usage: node supabase/nutrition/integration/v5-origin.integration.mjs <@electric-sql/pglite directory>');
const { PGlite } = await import(pathToFileURL(join(resolve(runtime), 'dist/index.js')).href);
const { pgcrypto } = await import(pathToFileURL(join(resolve(runtime), 'dist/contrib/pgcrypto.js')).href);
const db = new PGlite({ extensions: { pgcrypto } });
const directory = import.meta.dirname;
const migrations = resolve(directory, '../supabase/migrations');
const read = file => readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
try {
  // Auth functions are local bootstrap stubs; SQL grants/RLS run in PostgreSQL.
  // HTTP JWT signatures and Supabase Auth/PostgREST are outside this test.
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema extensions;
    create extension pgcrypto with schema extensions;
    create function auth.jwt() returns jsonb language sql stable as $$
      select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb) $$;
    create function auth.uid() returns uuid language sql stable as $$
      select coalesce(nullif(current_setting('request.jwt.claim.sub', true), ''), auth.jwt()->>'sub')::uuid $$;
    create function auth.role() returns text language sql stable as $$ select auth.jwt()->>'role' $$;
    grant usage on schema auth, extensions to anon, authenticated, service_role;
    grant execute on all functions in schema auth to anon, authenticated, service_role;
  `);
  const files = readdirSync(migrations).filter(file => file.endsWith('.sql')).sort();
  for (const file of files) {
    if (file === '20260814065823_backfill_kaguri_pricetrace_metadata.sql') {
      // Existing historical replay prerequisite: synthetic row, original SQL unchanged.
      const sql = read(join(migrations, file));
      const selectors = [...new Set(sql.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g))];
      assert.equal(selectors.length, 1);
      await db.exec(read(join(directory, 'historical-replay/pre_20260814065823_kaguri.sql'))
        .replaceAll('{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}', selectors[0]));
    }
    try { await db.exec(read(join(migrations, file))); }
    catch (error) { throw new Error(`Migration ${file}: ${error.code} ${error.message}`); }
  }
  console.log(`UNCHANGED_MIGRATIONS_PASS ${files.length} (synthetic historical prerequisite)`);
  for (const file of ['ocr-v5-origin-compatibility.sql', 'ocr-dining-out-publication-rollback.sql']) {
    try {
      const result = await db.exec(read(join(directory, file)));
      console.log(`SQL_FIXTURE_PASS ${file}`, result.flatMap(item => item.rows).filter(row => row.result));
    } catch (error) { throw new Error(`Fixture ${file}: ${error.code} ${error.message} ${error.where || ''}`); }
  }
} finally { await db.close(); }
