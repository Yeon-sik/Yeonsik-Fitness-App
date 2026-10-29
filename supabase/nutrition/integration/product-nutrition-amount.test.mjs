import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const directory = path.dirname(fileURLToPath(import.meta.url));
const migrations = path.resolve(directory, '../supabase/migrations');
const migration = readFileSync(path.join(migrations, '20261029060700_public_product_nutrition_amount_projection.sql'), 'utf8');
const catalogId = '22222222-2222-4222-8222-222222222222';
const standardId = '11111111-1111-4111-8111-111111111111';
const pinnedId = '44444444-4444-4444-8444-444444444444';
const revision = `sha256:${'a'.repeat(64)}`;
function functionSql(file, name) {
  const sql = readFileSync(path.join(migrations, file), 'utf8');
  const matches = [...sql.matchAll(new RegExp(`create (?:or replace )?function public\\.${name}\\([\\s\\S]*?\\n\\$\\$;`, 'gi'))];
  assert.equal(matches.length, 1, `unique real historical source: ${name}`);
  return matches[0][0];
}
function productRead(overrides = {}) {
  return { schemaVersion: 'product-read.v1', namespace: 'pricetrace', revision,
    products: [{ revision, standardProduct: { id: standardId }, catalogProduct: {
      id: catalogId, contentAmount: 130, contentUnit: 'g', packageCount: 1, ...overrides,
    } }],
  };
}

await test('Nutrition read-time package projection in disposable PostgreSQL', async t => {
  const db = await PGlite.create();
  const query = async (sql, params = []) => (await db.query(sql, params)).rows;
  const scalar = async (sql, params = []) => Object.values((await query(sql, params))[0])[0];
  const read = (id = catalogId, namespace = 'pricetrace') => query('select * from public.get_public_product_nutrition_v1($1,$2)', [namespace, id]);
  const register = (document = productRead(), id = catalogId) => scalar(
    'select public.register_product_nutrition_read_spec_v1($1,$2::jsonb)', [id, JSON.stringify(document)]);
  const asRole = async (role, action) => {
    await db.exec(`set role ${role}`);
    await query("select set_config('request.jwt.claim.role',$1,false)", [role]);
    try { return await action(); }
    finally { await db.exec('reset role'); await query("select set_config('request.jwt.claim.role','',false)"); }
  };
  try {
    await db.exec(`
      create role anon; create role authenticated; create role service_role bypassrls;
      create schema auth;
      create function auth.role() returns text language sql stable as $$ select nullif(current_setting('request.jwt.claim.role',true),'') $$;
      grant usage on schema public,auth to anon,authenticated,service_role;
      grant execute on function auth.role() to public;
      create table public.nutrition_foods (
        id text primary key,owner_id text, name text,kind text,basis_amount numeric,basis_unit text,prep_state text,
        calories_kcal numeric,protein_grams numeric,carbs_grams numeric,fat_grams numeric,sodium_mg numeric,
        saturated_fat_grams numeric,sugars_grams numeric,fiber_grams numeric,added_sugars_grams numeric,trans_fat_grams numeric,cholesterol_mg numeric,
        source_type text,source_reference text,source_version text,revision integer,
        visibility text,published_at timestamptz,deleted_at timestamptz);
      create table public.nutrition_food_nutrients (food_id text,nutrient_code text,amount numeric,unit text,deleted_at timestamptz);
      create table public.product_nutrition_links (
        id uuid primary key,owner_id text,nutrition_food_id text,catalog_product_id uuid,standard_product_id uuid,status text,
        reviewed_at timestamptz,created_at timestamptz,deleted_at timestamptz,revision integer,
        catalog_product_revision text,catalog_content_amount numeric,catalog_content_unit text,catalog_package_count integer,
        source_type text,proposal_reference text,proposal_source_revision text,product_contract_version text);
      create table public.test_meal_history (id integer, calories numeric);
      insert into public.test_meal_history values (1,315);
      insert into public.nutrition_foods(id,owner_id,name,kind,basis_amount,basis_unit,prep_state,
        calories_kcal,protein_grams,carbs_grams,fat_grams,sodium_mg,saturated_fat_grams,sugars_grams,
        source_type,source_reference,source_version,revision,visibility,published_at)
      values ('synthetic-food','synthetic-owner','시험 식품','ingredient',210,'g','as_served',
        315,6.3,63,2.1,21,0,null,'manual','synthetic-public-label','v1',1,'public',now()),
        ('pinned-food','synthetic-owner','동량 식품','ingredient',120,'g','as_served',
        510,10,80,17,1700,8,3,'manual','synthetic-public-label','v1',1,'public',now());
      insert into public.nutrition_food_nutrients values ('synthetic-food','calcium',210,'mg',null);
      insert into public.product_nutrition_links(id,owner_id,nutrition_food_id,catalog_product_id,standard_product_id,status,revision,created_at,reviewed_at,
        catalog_product_revision,catalog_content_amount,catalog_content_unit,catalog_package_count)
      values ('33333333-3333-4333-8333-333333333333','synthetic-owner','synthetic-food','${catalogId}','${standardId}','approved',1,now(),now(),null,null,null,null),
        ('55555555-5555-4555-8555-555555555555','synthetic-owner','pinned-food','${pinnedId}','${standardId}','approved',1,now(),now(),'${revision}',120,'g',1);
    `);
    await db.exec(functionSql('20260814065526_product_nutrition_link_pricetrace_metadata.sql', 'get_public_product_nutrition_v1'));
    await db.exec(functionSql('20260817120000_product_nutrition_link_contract.sql', 'guard_product_nutrition_link_update'));
    await db.exec(functionSql('20260810120000_harden_product_nutrition_publication.sql', 'nutrition_product_basis_matches_v1'));
    await db.exec(`create trigger product_nutrition_links_update_guard before update or delete on public.product_nutrition_links
      for each row execute function public.guard_product_nutrition_link_update();
      revoke all on function public.get_public_product_nutrition_v1(text,uuid) from public;
      grant execute on function public.get_public_product_nutrition_v1(text,uuid) to anon,authenticated,service_role;`);
    const originals = await scalar(`select jsonb_build_object('foods',(select jsonb_agg(to_jsonb(f) order by id) from public.nutrition_foods f),
      'links',(select jsonb_agg(to_jsonb(l) order by id) from public.product_nutrition_links l),
      'nutrients',(select jsonb_agg(to_jsonb(n)) from public.nutrition_food_nutrients n),
      'meals',(select jsonb_agg(to_jsonb(m)) from public.test_meal_history m))`);
    const guard = await scalar("select pg_get_functiondef('public.guard_product_nutrition_link_update()'::regprocedure)");
    const basisGuard = await scalar("select pg_get_functiondef('public.nutrition_product_basis_matches_v1(numeric,text,numeric,text)'::regprocedure)");
    const returnType = await scalar("select pg_get_function_result('public.get_public_product_nutrition_v1(text,uuid)'::regprocedure)");
    await db.exec(migration);

    await t.test('preserves the exact 18-column public signature and strict existing guards', async () => {
      assert.equal(await scalar("select pg_get_function_result('public.get_public_product_nutrition_v1(text,uuid)'::regprocedure)"), returnType);
      assert.equal(await scalar("select pg_get_functiondef('public.guard_product_nutrition_link_update()'::regprocedure)"), guard);
      assert.equal(await scalar("select pg_get_functiondef('public.nutrition_product_basis_matches_v1(numeric,text,numeric,text)'::regprocedure)"), basisGuard);
      assert.equal(await scalar("select public.nutrition_product_basis_matches_v1(210,'g',130,'g')"), false);
    });
    await t.test('does not guess missing legacy product metadata', async () => {
      const [row] = await read(); assert.equal(Number(row.basis_amount),210); assert.equal(row.source_type,'manual');
    });
    for (const role of ['anon','authenticated']) {
      await t.test(`${role} cannot read specs, register amounts, or call private source/scaling helpers`, async () => {
        await asRole(role, async () => {
          await assert.rejects(() => query('select * from public.product_nutrition_read_specs'), { code:'42501' });
          await assert.rejects(() => register(), { code:'42501' });
          await assert.rejects(() => query("select * from public._get_public_product_nutrition_source_v1('pricetrace',$1)",[catalogId]),{ code:'42501' });
          await assert.rejects(() => query("select public.nutrition_read_scale_factor_v1(210,'g',130,'g')"),{ code:'42501' });
        });
      });
    }
    await t.test('RLS is enabled and service_role has no direct write grant', async () => {
      assert.equal(await scalar("select relrowsecurity from pg_class where oid='public.product_nutrition_read_specs'::regclass"),true);
      assert.equal(await scalar("select has_table_privilege('service_role','public.product_nutrition_read_specs','INSERT')"),false);
      assert.equal(await scalar("select has_table_privilege('service_role','public.product_nutrition_read_specs','UPDATE')"),false);
    });
    await t.test('requires a trusted service caller even if the function owner invokes it', async () => {
      await assert.rejects(() => register(),{ code:'42501' });
    });
    await t.test('registers only exact public approved identity and replays idempotently', async () => {
      const id = await asRole('service_role',() => register());
      assert.equal(await asRole('service_role',() => register()),id);
      assert.equal(Number(await scalar('select count(*) from public.product_nutrition_read_specs')),1);
    });
    await t.test('returns 130g 195kcal to the unchanged anonymous public call', async () => {
      const [row] = await asRole('anon',() => read());
      assert.equal(Number(row.basis_amount),130); assert.equal(row.basis_unit,'g');
      assert.equal(Number(row.nutrition_values.calories_kcal),195);
      assert.equal(Number(row.nutrition_values.protein_grams),3.9);
      assert.equal(Number(row.micronutrients.calcium.amount),130); assert.equal(row.micronutrients.calcium.unit,'mg');
      assert.equal(row.nutrition_values.sugars_grams,null); assert.equal(Number(row.nutrition_values.saturated_fat_grams),0);
      assert.equal(row.source_type,'calculated_product_amount');
      const evidence = JSON.parse(row.source_reference);
      assert.equal(evidence.isEstimate,true); assert.equal(evidence.sourceBasis.amount,210);
      assert.equal(evidence.sourceNutritionValues.calories_kcal,315);
      assert.equal(evidence.targetBasis.scope,'per_package'); assert.ok(evidence.notice.includes('추정치'));
      assert.ok(row.source_revision.startsWith('nutrition-product-amount.v1:'));
    });
    await t.test('does not multiply nutrition by package count', async () => {
      const document = productRead({packageCount:10}); document.products[0].revision=`sha256:${'b'.repeat(64)}`;
      await asRole('service_role',() => register(document));
      const [row] = await read(); assert.equal(row.catalog_package_count,10); assert.equal(Number(row.nutrition_values.calories_kcal),195);
    });
    await t.test('keeps an equal pinned basis and original source metadata unchanged', async () => {
      const [row] = await read(pinnedId); assert.equal(Number(row.basis_amount),120);
      assert.equal(Number(row.nutrition_values.calories_kcal),510); assert.equal(row.source_type,'manual');
      assert.equal(row.source_reference,'synthetic-public-label'); assert.equal(row.source_revision,'v1');
    });
    for (const [label,mutate] of [
      ['wrong catalog',d=>{d.products[0].catalogProduct.id=pinnedId;}],
      ['wrong family',d=>{d.products[0].standardProduct.id=pinnedId;}],
      ['wrong namespace',d=>{d.namespace='other';}],
      ['wrong contract',d=>{d.schemaVersion='other';}],
      ['ambiguous products',d=>{d.products.push(d.products[0]);}],
      ['zero amount',d=>{d.products[0].catalogProduct.contentAmount=0;}],
      ['unknown unit',d=>{d.products[0].catalogProduct.contentUnit='serving';}],
      ['fractional package count',d=>{d.products[0].catalogProduct.packageCount=1.5;}],
      ['missing revision',d=>{delete d.products[0].revision;}],
    ]) await t.test(`rejects server snapshot: ${label}`,async()=>{
      const document=productRead(); mutate(document);
      await assert.rejects(()=>asRole('service_role',()=>register(document)),{code:'23514'});
    });
    await t.test('rejects changed values with a reused product revision', async () => {
      await assert.rejects(()=>asRole('service_role',()=>register(productRead({contentAmount:140}))),{code:'23514'});
    });
    for (const [sourceAmount,sourceUnit,targetAmount,targetUnit,expected] of [
      [210,'g',130,'g',130/210],[0.21,'kg',210,'g',1],[210000,'mg',210,'g',1],[0.25,'l',250,'ml',1],
      [210,'grams',130,'g',130/210],[2,'pieces',1,'each',0.5],
      [210,'g',130,'ml',null],[210,'ml',130,'g',null],[1,'serving',130,'g',null],
      [0,'g',130,'g',null],[-1,'g',130,'g',null],['NaN','g',130,'g',null],['Infinity','g',130,'g',null],
    ]) await t.test(`unit scale ${sourceAmount}${sourceUnit} to ${targetAmount}${targetUnit}`,async()=>{
      const result=await scalar('select public.nutrition_read_scale_factor_v1($1,$2,$3,$4)',[sourceAmount,sourceUnit,targetAmount,targetUnit]);
      if(expected===null) assert.equal(result,null); else assert.ok(Math.abs(Number(result)-expected)<1e-12);
    });
    await t.test('rejects invalid/missing nutrient values rather than turning them into zero', async()=>{
      for(const values of [{kcal:-1},{kcal:'NaN'},[]]) assert.equal(await scalar('select public.nutrition_scale_read_values_v1($1::jsonb,1,false)',[JSON.stringify(values)]),null);
    });
    await t.test('unknown namespace and unlinked products reveal no candidates',async()=>{
      assert.deepEqual(await read(catalogId,'other'),[]); assert.deepEqual(await read(standardId),[]);
    });
    await t.test('the public link update guard still rejects mutation',async()=>{
      await assert.rejects(()=>query('update public.product_nutrition_links set catalog_content_amount=130 where catalog_product_id=$1',[catalogId]),{code:'55000'});
    });
    await t.test('unpublishing or revoking approval immediately removes the public result',async()=>{
      await db.exec('begin');
      try {
        await query("update public.nutrition_foods set visibility='private' where id='synthetic-food'");
        assert.deepEqual(await asRole('anon',()=>read()),[]);
        await query("update public.product_nutrition_links set status='rejected' where catalog_product_id=$1",[catalogId]);
        await query("update public.nutrition_foods set visibility='public' where id='synthetic-food'");
        assert.deepEqual(await asRole('anon',()=>read()),[]);
      } finally { await db.exec('rollback'); }
    });
    await t.test('source revision changes invalidate a legacy read spec',async()=>{
      await db.exec('begin');
      try {
        await query("update public.nutrition_foods set revision=revision+1 where id='synthetic-food'");
        const [row]=await read(); assert.equal(Number(row.basis_amount),210); assert.equal(row.source_type,'manual');
      } finally { await db.exec('rollback'); }
    });
    await t.test('an incompatible target unit produces no invented nutrition',async()=>{
      await db.exec('begin');
      try {
        const document=productRead({contentUnit:'ml'}); document.products[0].revision=`sha256:${'c'.repeat(64)}`;
        await asRole('service_role',()=>register(document));
        assert.deepEqual(await asRole('anon',()=>read()),[]);
      } finally { await db.exec('rollback'); }
    });
    await t.test('private read specs do not block the normal deletion of a source link',async()=>{
      await db.exec('begin');
      try {
        await query("update public.nutrition_foods set visibility='private' where id='synthetic-food'");
        await query('delete from public.product_nutrition_links where catalog_product_id=$1',[catalogId]);
        assert.equal(Number(await scalar('select count(*) from public.product_nutrition_read_specs')),0);
      } finally { await db.exec('rollback'); }
    });
    await t.test('source foods, links, micronutrients and Meal history remain byte-equivalent',async()=>{
      const after=await scalar(`select jsonb_build_object('foods',(select jsonb_agg(to_jsonb(f) order by id) from public.nutrition_foods f),
        'links',(select jsonb_agg(to_jsonb(l) order by id) from public.product_nutrition_links l),
        'nutrients',(select jsonb_agg(to_jsonb(n)) from public.nutrition_food_nutrients n),
        'meals',(select jsonb_agg(to_jsonb(m)) from public.test_meal_history m))`);
      assert.deepEqual(after,originals);
    });
  } finally { await db.close(); }
});
