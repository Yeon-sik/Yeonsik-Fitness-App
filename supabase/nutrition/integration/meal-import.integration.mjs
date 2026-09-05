import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const integrationDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(integrationDirectory, '../../..');
const envPath = process.env.NUTRITION_INTEGRATION_ENV_FILE
  || path.join(repositoryRoot, 'supabase', '.env');
dotenv.config({ path: envPath, quiet: true });

const baseUrl = firstEnv(
  'NUTRITION_DB_URL',
  'NUTRITION_SUPABASE_URL',
  'SUPABASE_URL'
)?.replace(/\/$/, '');
const anonKey = firstEnv(
  'NUTRITION_DB_ANON',
  'NUTRITION_SUPABASE_ANON_KEY',
  'SUPABASE_ANON_KEY'
);
const serviceRoleKey = firstEnv(
  'NUTRITION_INTEGRATION_SERVICE_ROLE_KEY',
  'NUTRITION_DB_SERVICE_ROLE',
  'SUPABASE_SERVICE_ROLE_KEY'
);
const RUN_ID = uniqueId('run');

class ApiError extends Error {
  constructor(label, status, body) {
    super(`${label} returned HTTP ${status}: ${errorMessage(body)}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

class ConfigurationError extends Error {}

const created = {
  userIds: new Set(),
  foodIds: new Set(),
  micronutrientIds: new Set(),
  componentImportIds: new Set(),
  mealImportIds: new Set(),
  mealIds: new Set(),
};

function firstEnv(...names) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) return value;
  }
  return null;
}

function errorMessage(body) {
  if (!body) return 'empty response';
  if (typeof body === 'string') return body.slice(0, 300);
  return String(body.message || body.error_description || body.error || JSON.stringify(body)).slice(0, 300);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function assertEqual(actual, expected, message) {
  assert(actual === expected, `${message}: expected ${expected}, got ${actual}`);
}

function assertClose(actual, expected, message) {
  assert(Math.abs(Number(actual) - expected) < 0.000001,
    `${message}: expected ${expected}, got ${actual}`);
}

function uniqueId(prefix) {
  return `${prefix}-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
}

function integrationRef(suffix) {
  return `integration://nutrition-meal/${RUN_ID}/${suffix}`;
}

function authHeaders(accessToken, key = anonKey) {
  return {
    apikey: key,
    Authorization: `Bearer ${accessToken || key}`,
    'Content-Type': 'application/json',
  };
}

async function request(label, endpoint, options = {}) {
  const response = await fetch(`${baseUrl}${endpoint}`, options);
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { label, status: response.status, ok: response.ok, body };
}

async function expectOk(label, endpoint, options = {}) {
  const result = await request(label, endpoint, options);
  if (!result.ok) throw new ApiError(label, result.status, result.body);
  return result.body;
}

async function expectRejected(label, endpoint, options = {}) {
  const result = await request(label, endpoint, options);
  assert(!result.ok, `${label}: expected rejection, got HTTP ${result.status}`);
  console.log(`PASS ${label} (${result.status})`);
  return result;
}

async function signIn(email, password) {
  const body = await expectOk(
    `sign in ${email}`,
    '/auth/v1/token?grant_type=password',
    {
      method: 'POST',
      headers: authHeaders(null),
      body: JSON.stringify({ email, password }),
    },
  );
  assert(body?.access_token && body?.user?.id, `sign in ${email}: missing session`);
  return { email, password, accessToken: body.access_token, userId: body.user.id };
}

async function createAdminUser(label) {
  const email = `nutrition-meal-${RUN_ID}-${label}@example.com`;
  const password = `Nutr!tion-${crypto.randomUUID()}-Aa1`;
  const body = await expectOk(
    `create integration owner ${label}`,
    '/auth/v1/admin/users',
    {
      method: 'POST',
      headers: authHeaders(null, serviceRoleKey),
      body: JSON.stringify({ email, password, email_confirm: true }),
    },
  );
  assert(body?.id, `create integration owner ${label}: missing user id`);
  created.userIds.add(body.id);
  return signIn(email, password);
}

async function resolveOwners() {
  if (!serviceRoleKey) {
    throw new ConfigurationError('NUTRITION_INTEGRATION_SERVICE_ROLE_KEY is required for cleanup.');
  }
  const emailA = firstEnv('NUTRITION_INTEGRATION_EMAIL_A', 'NUTRITION_INTEGRATION_EMAIL');
  const passwordA = firstEnv('NUTRITION_INTEGRATION_PASSWORD_A', 'NUTRITION_INTEGRATION_PASSWORD');
  const emailB = firstEnv('NUTRITION_INTEGRATION_EMAIL_B');
  const passwordB = firstEnv('NUTRITION_INTEGRATION_PASSWORD_B');
  const ownerA = emailA && passwordA
    ? await signIn(emailA, passwordA)
    : await createAdminUser('a');
  const ownerB = emailB && passwordB
    ? await signIn(emailB, passwordB)
    : await createAdminUser('b');
  assert(ownerA.userId !== ownerB.userId, 'integration owners must be different users');
  return { ownerA, ownerB };
}

function rpcHeaders(owner) {
  return authHeaders(owner.accessToken);
}

async function callRpc(owner, functionName, payload) {
  const body = await expectOk(
    `${functionName} ${payload.p_idempotency_key}`,
    `/rest/v1/rpc/${functionName}`,
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload),
    },
  );
  assert(Array.isArray(body) && body.length === 1, `${functionName} must return exactly one row`);
  return body[0];
}

async function getRows(owner, table, filters, select = '*') {
  const query = new URLSearchParams({ select });
  for (const [column, value] of Object.entries(filters)) query.set(column, `eq.${value}`);
  return expectOk(
    `select ${table}`,
    `/rest/v1/${table}?${query.toString()}`,
    { method: 'GET', headers: rpcHeaders(owner) },
  );
}

const REQUIRED_NUTRIENTS = [
  'calories_kcal',
  'carbs_grams',
  'protein_grams',
  'fat_grams',
  'sugars_grams',
  'saturated_fat_grams',
  'sodium_mg',
];

function componentValues() {
  return {
    calories_kcal: 300,
    carbs_grams: 30,
    protein_grams: 20,
    fat_grams: 10,
    sugars_grams: 5,
    saturated_fat_grams: 3,
    sodium_mg: 700,
  };
}

function componentPayload(key) {
  const values = componentValues();
  const provenance = Object.fromEntries(REQUIRED_NUTRIENTS.map((nutrient) => [nutrient, {
    value: values[nutrient],
    value_status: 'estimated',
    source_type: 'food_image_estimate',
    evidence_refs: [integrationRef(`component/photo/${nutrient}`)],
  }]));
  return {
    p_idempotency_key: key,
    p_source_document_ref: integrationRef('component/document'),
    p_food_name: 'Integration 무료 반찬',
    p_brand: 'Integration Restaurant',
    p_category: 'recipe',
    p_basis_amount: 100,
    p_basis_unit: 'g',
    p_required_nutrients: values,
    p_nutrient_provenance: provenance,
    p_optional_nutrients: { fiber_grams: 2 },
    p_provenance: {
      schema_version: 'yeonsik-ocr.v2',
      estimated: true,
      source_version: 'integration-component-v1',
      restaurant_menu_id: null,
    },
    p_user_verified: true,
    p_pricetrace_identity: {
      namespace: 'pricetrace',
      restaurant_id: '11111111-1111-4111-8111-111111111111',
      restaurant_location_id: '22222222-2222-4222-8222-222222222222',
      restaurant_menu_id: null,
    },
    p_estimation_evidence: {
      confidence: 0.84,
      range: { calories_kcal: { min: 240, point: 300, max: 360 } },
    },
  };
}

async function addMicronutrient(owner, foodId) {
  const id = crypto.randomUUID();
  await expectOk(
    'add integration micronutrient',
    '/rest/v1/nutrition_food_nutrients',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({
        id,
        owner_id: owner.userId,
        food_id: foodId,
        nutrient_code: 'iron',
        amount: 3.2,
        unit: 'mg',
      }),
    },
  );
  created.micronutrientIds.add(id);
}

function mealPayload(key, foodId) {
  return {
    p_idempotency_key: key,
    p_eaten_at: new Date(Date.now() - 60 * 1000).toISOString(),
    p_items: [{
      client_key: 'component-free-side-1',
      nutrition_food_id: foodId,
      amount: 50,
      unit: 'g',
      confidence: 0.91,
      source_provenance: {
        schema_version: 'yeonsik-ocr.v2',
        source_document_ref: integrationRef('meal/document'),
        artifact_key: 'nutrition:component-free-side-1',
        verified_consumption: true,
      },
    }],
    p_source: {
      schema_version: 'yeonsik-ocr.v2',
      projection: 'FITNESS_MEAL',
      source_app: 'ocr-app',
      meal_kind: 'dining_out',
      menu: 'Integration free side meal',
      source_document_ref: integrationRef('meal/document'),
      restaurant_menu_id: null,
    },
    p_pricetrace_identity: {
      namespace: 'pricetrace',
      restaurant_id: '11111111-1111-4111-8111-111111111111',
      restaurant_location_id: '22222222-2222-4222-8222-222222222222',
      restaurant_menu_id: null,
    },
  };
}

async function verifyComponent(owner, result) {
  assert(result.component_import_id, 'component result is missing import id');
  assertEqual(result.idempotent_replay, false, 'component first import replay');
  assertEqual(result.source_type, 'meal_component_estimate', 'component source type');
  assertEqual(result.visibility, 'private', 'component visibility');
  created.componentImportIds.add(result.component_import_id);
  created.foodIds.add(result.nutrition_food_id);

  const imports = await getRows(owner, 'nutrition_meal_component_imports', {
    id: result.component_import_id,
  });
  assertEqual(imports.length, 1, 'component import row');
  assertEqual(imports[0].nutrition_food_id, result.nutrition_food_id, 'component exact food id');
  const provenance = await getRows(owner, 'nutrition_meal_component_nutrient_provenance', {
    component_import_id: result.component_import_id,
  });
  assertEqual(provenance.length, 7, 'component provenance row count');
  console.log('PASS meal_component_estimate -> private NutritionFood + seven evidence rows');
}

async function verifyMeal(owner, result, payload) {
  assert(result.meal_import_id && result.meal_record_id, 'Meal result is missing IDs');
  assertEqual(result.idempotent_replay, false, 'Meal first import replay');
  assertEqual(result.item_count, 1, 'Meal item count');
  assertEqual(result.nutrition_food_ids[0], payload.p_items[0].nutrition_food_id, 'Meal exact Nutrition ID');
  created.mealImportIds.add(result.meal_import_id);
  created.mealIds.add(result.meal_record_id);

  const meals = await getRows(owner, 'meal_records', { id: result.meal_record_id },
    'id,owner_id,date,eaten_at,restaurant_menu_id,calories,protein_grams,source_provenance,pricetrace_identity');
  assertEqual(meals.length, 1, 'Meal parent row');
  assertEqual(meals[0].owner_id, owner.userId, 'Meal owner');
  assertEqual(meals[0].restaurant_menu_id, null, 'nullable restaurant menu identity');
  assertEqual(meals[0].eaten_at, payload.p_eaten_at, 'preserved offset eaten_at');
  assertClose(meals[0].calories, 150, 'scaled Meal calories');
  assertClose(meals[0].protein_grams, 10, 'scaled Meal protein');

  const items = await getRows(owner, 'meal_record_items', { meal_record_id: result.meal_record_id },
    'id,nutrition_food_id,food_name_snapshot,consumed_amount,consumed_unit,quantity,unit,calories,protein_grams,source_type_snapshot,source_provenance,pricetrace_identity');
  assertEqual(items.length, 1, 'Meal item snapshot row');
  const item = items[0];
  assertEqual(item.nutrition_food_id, payload.p_items[0].nutrition_food_id, 'snapshot trace ID');
  assertEqual(item.food_name_snapshot, 'Integration 무료 반찬', 'snapshot food name');
  assertClose(item.consumed_amount, 50, 'actual consumed amount');
  assertEqual(item.consumed_unit, 'g', 'actual consumed unit');
  assertClose(item.quantity, 50, 'normalized consumed quantity');
  assertEqual(item.unit, 'g', 'normalized consumed unit');
  assertClose(item.calories, 150, 'snapshot calories');
  assertClose(item.protein_grams, 10, 'snapshot protein');
  assertEqual(item.source_type_snapshot, 'meal_component_estimate', 'snapshot provenance source');
  assertEqual(item.source_provenance.artifact_key, 'nutrition:component-free-side-1', 'item provenance');
  assertEqual(item.pricetrace_identity.restaurant_menu_id, null, 'item nullable menu identity');

  const micronutrients = await getRows(owner, 'meal_record_item_nutrients', {
    meal_record_item_id: item.id,
  }, 'nutrient_code,amount,unit');
  assertEqual(micronutrients.length, 1, 'micronutrient snapshot row');
  assertEqual(micronutrients[0].nutrient_code, 'iron', 'micronutrient code');
  assertClose(micronutrients[0].amount, 1.6, 'scaled micronutrient amount');
  assertEqual(micronutrients[0].unit, 'mg', 'micronutrient unit');
  console.log('PASS FITNESS_MEAL -> Meal/MealItem/snapshot/micronutrient rows');
}

async function verifyIsolation(ownerB, result) {
  assertEqual((await getRows(ownerB, 'meal_records', { id: result.meal_record_id })).length, 0,
    'other owner cannot read Meal');
  assertEqual((await getRows(ownerB, 'nutrition_foods', { id: result.nutrition_food_ids[0] })).length, 0,
    'other owner cannot read private NutritionFood');
  assertEqual((await getRows(ownerB, 'nutrition_meal_component_imports', {
    id: result.component_import_id,
  })).length, 0, 'other owner cannot read component import');
  console.log('PASS owner isolation for Meal and component NutritionFood');
}

async function verifyDirectWritesRejected(owner, result) {
  await expectRejected(
    'direct Meal parent insert bypass',
    '/rest/v1/meal_records',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        owner_id: owner.userId,
        date: new Date().toISOString().slice(0, 10),
        menu: 'direct bypass',
        eaten_at: new Date().toISOString(),
      }),
    },
  );
  await expectRejected(
    'direct Meal item insert bypass',
    '/rest/v1/meal_record_items',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        owner_id: owner.userId,
        meal_record_id: result.meal_record_id,
        nutrition_food_id: result.nutrition_food_id,
        food_name_snapshot: 'direct bypass',
        consumed_amount: 1,
        consumed_unit: 'g',
        quantity: 1,
        unit: 'g',
        basis_amount_snapshot: 100,
        basis_unit_snapshot: 'g',
        order_index: 0,
      }),
    },
  );
}

async function cleanupTable(table, column, values) {
  for (const value of values) {
    const query = new URLSearchParams({ [column]: `eq.${value}` });
    const result = await request(
      `cleanup ${table}`,
      `/rest/v1/${table}?${query.toString()}`,
      {
        method: 'DELETE',
        headers: { ...authHeaders(null, serviceRoleKey), Prefer: 'return=minimal' },
      },
    );
    if (!result.ok) throw new ApiError(`cleanup ${table}`, result.status, result.body);
  }
}

async function cleanup() {
  if (!serviceRoleKey) return;
  await cleanupTable('nutrition_food_nutrients', 'id', created.micronutrientIds);
  await cleanupTable('meal_record_item_nutrients', 'meal_record_id', created.mealIds);
  await cleanupTable('meal_record_items', 'meal_record_id', created.mealIds);
  await cleanupTable('meal_verified_imports', 'id', created.mealImportIds);
  await cleanupTable('meal_records', 'id', created.mealIds);
  await cleanupTable('nutrition_meal_component_nutrient_provenance', 'component_import_id', created.componentImportIds);
  await cleanupTable('nutrition_meal_component_imports', 'id', created.componentImportIds);
  await cleanupTable('nutrition_foods', 'id', created.foodIds);

  for (const userId of created.userIds) {
    const result = await request(
      `cleanup auth user ${userId}`,
      `/auth/v1/admin/users/${encodeURIComponent(userId)}`,
      { method: 'DELETE', headers: authHeaders(null, serviceRoleKey) },
    );
    if (!result.ok) throw new ApiError('cleanup auth user', result.status, result.body);
  }
  console.log('PASS integration cleanup');
}

async function run() {
  if (process.env.NUTRITION_MEAL_INTEGRATION_ALLOW_REMOTE !== 'true') {
    throw new ConfigurationError(
      'Set NUTRITION_MEAL_INTEGRATION_ALLOW_REMOTE=true to run this real Meal integration test.',
    );
  }
  if (!baseUrl || !anonKey) {
    throw new ConfigurationError('NUTRITION_DB_URL and NUTRITION_DB_ANON are required.');
  }

  const { ownerA, ownerB } = await resolveOwners();
  const component = await callRpc(
    ownerA,
    'import_meal_component_estimate_v1',
    componentPayload(uniqueId('component')),
  );
  await verifyComponent(ownerA, component);
  await addMicronutrient(ownerA, component.nutrition_food_id);

  const meal = mealPayload(uniqueId('meal'), component.nutrition_food_id);
  const mealResult = await callRpc(ownerA, 'import_verified_meal_v1', meal);
  mealResult.component_import_id = component.component_import_id;
  await verifyMeal(ownerA, mealResult, meal);
  await verifyIsolation(ownerB, mealResult);
  await verifyDirectWritesRejected(ownerA, mealResult);

  const replay = await callRpc(ownerA, 'import_verified_meal_v1', meal);
  assertEqual(replay.meal_import_id, mealResult.meal_import_id, 'Meal replay import id');
  assertEqual(replay.meal_record_id, mealResult.meal_record_id, 'Meal replay record id');
  assertEqual(replay.idempotent_replay, true, 'Meal replay flag');
  assertEqual((await getRows(ownerA, 'meal_records', { id: mealResult.meal_record_id })).length, 1,
    'Meal replay must not add parent rows');
  console.log('PASS Meal idempotency replay');

  const collision = {
    ...meal,
    p_source: { ...meal.p_source, menu: 'changed payload' },
  };
  await expectRejected(
    'Meal idempotency collision',
    '/rest/v1/rpc/import_verified_meal_v1',
    {
      method: 'POST',
      headers: rpcHeaders(ownerA),
      body: JSON.stringify(collision),
    },
  );
  console.log('PASS verified Meal integration checks');
}

let runError = null;
try {
  await run();
} catch (error) {
  runError = error;
  if (error instanceof ConfigurationError) {
    console.error(`CONFIGURATION: ${error.message}`);
  } else {
    console.error(`FAIL: ${error.stack || error.message}`);
  }
} finally {
  try {
    await cleanup();
  } catch (cleanupError) {
    console.error(`CLEANUP FAIL: ${cleanupError.stack || cleanupError.message}`);
    runError ||= cleanupError;
  }
}

if (runError) process.exitCode = 1;
