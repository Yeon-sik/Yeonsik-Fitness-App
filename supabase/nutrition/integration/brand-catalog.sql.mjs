// Actual PostgreSQL replay in PGlite; no network, real-user rows, or SQL text mocks.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const { PGlite } = await import(process.argv[2] ? pathToFileURL(path.resolve(process.argv[2])).href : '@electric-sql/pglite');
const db = new PGlite();
const dir = fileURLToPath(new URL('../supabase/migrations/', import.meta.url));
const migration = '20261023121311_pricetrace_brand_catalog_consumer.sql';
const repair = '20261023143111_fix_nutrition_read_v4_contract_version.sql';
const fields = ['pricetrace_brand_id', 'pricetrace_brand_name_snapshot', 'pricetrace_parent_brand_id_snapshot', 'pricetrace_parent_brand_name_snapshot'];
const brand = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const parent = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const owner = '11111111-1111-4111-8111-111111111111';
const other = '22222222-2222-4222-8222-222222222222';
const query = async sql => (await db.query(sql)).rows;
// Replay Git's LF SQL, independent from Windows checkout autocrlf settings.
const sqlFile = file => readFileSync(file, 'utf8').replaceAll('\r\n', '\n');
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema extensions;
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    create function auth.role() returns text language sql stable as $$select nullif(current_setting('request.jwt.claim.role',true),'')$$;
    grant usage on schema public, auth to anon, authenticated, service_role;
    grant execute on all functions in schema auth to anon, authenticated, service_role;`);
  const files = readdirSync(dir).filter(f => f.endsWith('.sql') && f !== migration && f !== repair).sort();
  for (const name of files) {
    if (name.startsWith('20260814065823_')) {
      const fixture = sqlFile(new URL('historical-replay/pre_20260814065823_kaguri.sql', import.meta.url))
        .replaceAll('{{KAGURI_PRODUCT_UUID_FROM_HISTORICAL_SQL}}', '96eed0f6-1cfa-401a-850f-670d71c44d6f');
      await db.exec(fixture);
    }
    try { await db.exec(sqlFile(path.join(dir, name))); }
    catch (error) { throw new Error(`Baseline migration ${name}: ${error.message}`); }
  }
  const contracts = () => query(`select proname, pg_get_functiondef(p.oid) as definition from pg_proc p
    join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and proname in
    ('get_nutrition_read_v1','get_nutrition_read_v2','get_nutrition_read_v3') order by proname`);
  const before = await contracts();
  assert.equal(before.length, 3);
  await db.exec(sqlFile(new URL('historical-replay/snapshot_before_brand_catalog_v4.sql', import.meta.url)));
  await db.exec(sqlFile(path.join(dir, migration)));
  const v4Metadata = () => query(`select pg_get_function_arguments(oid) as args,
    pg_get_function_result(oid) as result, provolatile, prosecdef, proconfig, proacl
    from pg_proc where oid='public.get_nutrition_read_v4(text)'::regprocedure`);
  const metadataBefore = await v4Metadata();
  const triggerBefore = await query("select pg_get_functiondef('public.bump_nutrition_food_revision()'::regprocedure) as definition");
  await db.exec(sqlFile(path.join(dir, repair)));
  assert.deepEqual(await v4Metadata(), metadataBefore, 'v4 signature, shape, stability, invoker, search_path and grants unchanged');
  assert.deepEqual(await query("select pg_get_functiondef('public.bump_nutrition_food_revision()'::regprocedure) as definition"), triggerBefore);
  await db.exec(sqlFile(new URL('historical-replay/assert_brand_catalog_v4.sql', import.meta.url)));
  assert.deepEqual(await contracts(), before, 'v1/v2/v3 function definitions must be unchanged');
  await db.exec(`insert into nutrition_foods (id,owner_id,name,brand,manufacturer_name,brand_name,sub_brand_name,
    product_name,kind,basis_amount,basis_unit,visibility) values
    ('root','${owner}','제품','라벨','제조사','소스 브랜드',null,'소스 제품','external_menu',100,'g','private'),
    ('sub','${owner}','제품2','라벨',null,null,'소스 서브',null,'external_menu',100,'g','private'),
    ('unknown-parent','${owner}','제품3',null,null,null,null,null,'external_menu',100,'g','private'),
    ('public',null,'공개 제품',null,null,null,null,null,'external_menu',100,'g','public'),
    ('other','${other}','다른 소유자',null,null,null,null,null,'ingredient',100,'g','private');`);
  await db.exec(`update nutrition_foods set pricetrace_brand_id='${brand}', pricetrace_brand_name_snapshot='선택 브랜드' where id='root';
    update nutrition_foods set pricetrace_brand_id='${brand}', pricetrace_brand_name_snapshot='서브',
      pricetrace_parent_brand_id_snapshot='${parent}',pricetrace_parent_brand_name_snapshot='부모' where id='sub';
    update nutrition_foods set pricetrace_brand_id='${brand}', pricetrace_brand_name_snapshot='서브',
      pricetrace_parent_brand_id_snapshot='${parent}' where id='unknown-parent';`);
  for (const field of fields) {
    const beforeRevision = (await query("select revision from nutrition_foods where id='sub'"))[0].revision;
    const values = {pricetrace_brand_id: parent, pricetrace_brand_name_snapshot: '다른 이름',
      pricetrace_parent_brand_id_snapshot: brand, pricetrace_parent_brand_name_snapshot: '다른 부모'};
    await db.exec(`update nutrition_foods set ${field}='${values[field]}' where id='sub'`);
    assert.equal((await query("select revision from nutrition_foods where id='sub'"))[0].revision, beforeRevision + 1, field);
  }
  const revision = (await query("select revision from nutrition_foods where id='root'"))[0].revision;
  await db.exec("update nutrition_foods set updated_at=now() where id='root'");
  assert.equal((await query("select revision from nutrition_foods where id='root'"))[0].revision, revision);
  await assert.rejects(db.exec("update nutrition_foods set pricetrace_brand_id=null where id='root'"), /snapshot_check/);
  await assert.rejects(db.exec("update nutrition_foods set pricetrace_brand_id='bad-uuid' where id='root'"), /uuid/);
  const compareReads = async (search = null) => {
    const rows = async version => (await db.query(`select * from get_nutrition_read_v${version}($1)`, [search])).rows;
    const v3 = await rows(3), v4 = await rows(4);
    assert.ok(v3.every(r => r.contract_version === 'nutrition-read.v3'));
    assert.ok(v4.every(r => r.contract_version === 'nutrition-read.v4'));
    const common = (row, omitted) => Object.fromEntries(Object.entries(row).filter(([key]) => !omitted.includes(key)));
    assert.deepEqual(v4.map(r => common(r, ['contract_version', ...fields])),
      v3.map(r => common(r, ['contract_version'])), 'complete ordered business projections must match');
    return v4;
  };
  // Query as superuser to exercise every Brand fixture, then compare both RLS roles.
  await compareReads();
  await compareReads('제품');
  await compareReads('no-matching-food');
  await db.exec(`set role anon; select set_config('request.jwt.claim.sub','',false)`);
  assert.deepEqual(new Set((await query('select nutrition_food_id from get_nutrition_read_v4(null)')).map(r => r.nutrition_food_id)),
    new Set(['public', 'historical-replay-kaguri-food']));
  await compareReads();
  await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub','${owner}',false)`);
  const v4 = await compareReads();
  assert.ok(v4.some(r => r.nutrition_food_id === 'root'));
  assert.ok(!v4.some(r => r.nutrition_food_id === 'other'));
  const sub = v4.find(r => r.nutrition_food_id === 'sub');
  assert.equal(sub.pricetrace_brand_id, parent);
  assert.equal(sub.pricetrace_brand_name_snapshot, '다른 이름');
  assert.equal(sub.pricetrace_parent_brand_id_snapshot, brand);
  assert.equal(sub.pricetrace_parent_brand_name_snapshot, '다른 부모');
  assert.equal(v4.find(r => r.nutrition_food_id === 'root').pricetrace_parent_brand_id_snapshot, null);
  const unknown = v4.find(r => r.nutrition_food_id === 'unknown-parent');
  assert.equal(unknown.pricetrace_parent_brand_id_snapshot, parent);
  assert.equal(unknown.pricetrace_parent_brand_name_snapshot, null);
  const blocked = await query(`update nutrition_foods set pricetrace_brand_id='${brand}',
    pricetrace_brand_name_snapshot='공격' where id='other' returning id`);
  assert.equal(blocked.length, 0);
  await assert.rejects(db.exec(`insert into nutrition_foods(id,owner_id,name,kind,basis_amount,basis_unit)
    values('forbidden','${other}','공격','ingredient',1,'g')`), /row-level security/);
  await db.exec(`update nutrition_foods set ${fields.map(f => `${f}=null`).join(',')} where id='root'`);
  assert.ok(fields.every(f => (v4.find(r => r.nutrition_food_id === 'public'))[f] === null));
  const cleared = (await query("select * from get_nutrition_read_v4(null) where nutrition_food_id='root'"))[0];
  assert.ok(fields.every(f => cleared[f] === null));
  assert.equal(cleared.brand, '라벨'); assert.equal(cleared.manufacturer_name, '제조사');
  await compareReads();
  await db.exec(`select set_config('request.jwt.claim.sub','${other}',false)`);
  const otherRows = await compareReads();
  assert.ok(otherRows.some(r => r.nutrition_food_id === 'other'));
  assert.ok(!otherRows.some(r => r.nutrition_food_id === 'root'));
  console.log(JSON.stringify({pass:true, baselineMigrations:files.length, v1v2v3DefinitionsUnchanged:true,
    v3Discriminator:'nutrition-read.v3', v4Discriminator:'nutrition-read.v4', v4BusinessProjectionEqualsV3:true, revisionFields:4, rootSubUnknownAndClear:true, ownerAndAnonymousRls:true}));
} finally { await db.close(); }
