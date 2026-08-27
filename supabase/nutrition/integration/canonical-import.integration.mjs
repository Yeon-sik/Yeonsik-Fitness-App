import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const integrationDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(integrationDirectory, '../../..');
const envPath = process.env.NUTRITION_INTEGRATION_ENV_FILE
  || path.join(repositoryRoot, 'supabase', '.env');
dotenv.config({ path: envPath, quiet: true });

const REQUIRED_NUTRIENTS = [
  'calories_kcal',
  'carbs_grams',
  'protein_grams',
  'fat_grams',
  'sugars_grams',
  'saturated_fat_grams',
  'sodium_mg'
];

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
  foodIds: new Set(),
  canonicalImportIds: new Set(),
  projectionImportIds: new Set(),
  estimationEvidenceIds: new Set(),
  userIds: new Set()
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

function uniqueId(prefix) {
  return `${prefix}-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
}

function integrationRef(suffix) {
  return `integration://nutrition-canonical/${RUN_ID}/${suffix}`;
}

const RUN_ID = uniqueId('run');

function authHeaders(accessToken, key = anonKey) {
  return {
    apikey: key,
    Authorization: `Bearer ${accessToken || key}`,
    'Content-Type': 'application/json'
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

function bodyJson(body) {
  return body === undefined ? undefined : JSON.stringify(body);
}

async function expectOk(label, endpoint, options = {}) {
  const result = await request(label, endpoint, options);
  if (!result.ok) throw new ApiError(label, result.status, result.body);
  return result.body;
}

async function expectRejected(label, endpoint, options = {}) {
  const result = await request(label, endpoint, options);
  assert(!result.ok, `${label}: expected a rejected request, got HTTP ${result.status} ${bodyJson(result.body)}`);
  console.log(`PASS ${label} (${result.status})`);
  return result;
}

async function assertFunctionRejected(label, callback) {
  try {
    await callback();
  } catch (error) {
    assert(error instanceof ApiError, `${label}: unexpected error ${error.message}`);
    console.log(`PASS ${label} (${error.status})`);
    return;
  }
  throw new Error(`${label}: expected the function call to be rejected`);
}

async function signIn(email, password) {
  const body = await expectOk(
    `sign in ${email}`,
    '/auth/v1/token?grant_type=password',
    {
      method: 'POST',
      headers: authHeaders(null),
      body: JSON.stringify({ email, password })
    }
  );
  assert(body?.access_token && body?.user?.id, `sign in ${email}: missing access token or user id`);
  return { email, password, accessToken: body.access_token, userId: body.user.id };
}

async function createAdminUser(label) {
  const email = `nutrition-canonical-${RUN_ID}-${label}@example.com`;
  const password = `Nutr!tion-${crypto.randomUUID()}-Aa1`;
  const body = await expectOk(
    `create integration owner ${label}`,
    '/auth/v1/admin/users',
    {
      method: 'POST',
      headers: authHeaders(null, serviceRoleKey),
      body: JSON.stringify({ email, password, email_confirm: true })
    }
  );
  assert(body?.id, `create integration owner ${label}: missing user id`);
  created.userIds.add(body.id);
  return signIn(email, password);
}

async function resolveOwners() {
  const emailA = firstEnv('NUTRITION_INTEGRATION_EMAIL_A', 'NUTRITION_INTEGRATION_EMAIL');
  const passwordA = firstEnv('NUTRITION_INTEGRATION_PASSWORD_A', 'NUTRITION_INTEGRATION_PASSWORD');
  const emailB = firstEnv('NUTRITION_INTEGRATION_EMAIL_B');
  const passwordB = firstEnv('NUTRITION_INTEGRATION_PASSWORD_B');

  if (!serviceRoleKey) {
    throw new ConfigurationError(
      'NUTRITION_INTEGRATION_SERVICE_ROLE_KEY is required so the test can clean up its real database rows.'
    );
  }

  let ownerA;
  let ownerB;
  if (emailA && passwordA) {
    ownerA = await signIn(emailA, passwordA);
  } else {
    ownerA = await createAdminUser('a');
  }

  if (emailB && passwordB) {
    ownerB = await signIn(emailB, passwordB);
  } else {
    ownerB = await createAdminUser('b');
  }
  assert(ownerA.userId !== ownerB.userId, 'integration owners must be different users');
  return { ownerA, ownerB };
}

function rpcHeaders(owner) {
  return authHeaders(owner.accessToken);
}

async function callCanonical(owner, payload) {
  const body = await expectOk(
    `canonical import ${payload.p_idempotency_key}`,
    '/rest/v1/rpc/import_canonical_nutrition_v2',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload)
    }
  );
  assert(Array.isArray(body) && body.length === 1, 'canonical import must return exactly one row');
  return body[0];
}

async function callLegacy(owner, payload) {
  const body = await expectOk(
    `legacy import ${payload.p_idempotency_key}`,
    '/rest/v1/rpc/import_verified_nutrition_v1',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload)
    }
  );
  assert(Array.isArray(body) && body.length === 1, 'legacy import must return exactly one row');
  return body[0];
}

function requiredValues(seed) {
  return {
    calories_kcal: seed,
    carbs_grams: 20 + seed / 10,
    protein_grams: 15 + seed / 20,
    fat_grams: 8 + seed / 30,
    sugars_grams: 3 + seed / 40,
    saturated_fat_grams: 2 + seed / 50,
    sodium_mg: 180 + seed
  };
}

function provenanceFor(values, sourceTypes, valueStatus, refPrefix) {
  return Object.fromEntries(REQUIRED_NUTRIENTS.map((key, index) => ({
    [key]: {
      value: values[key],
      value_status: valueStatus,
      source_type: sourceTypes[index],
      evidence_refs: [integrationRef(`${refPrefix}/${key}`)]
    }
  })));
}

function labelPayload(idempotencyKey, name, documentRef = idempotencyKey) {
  const values = requiredValues(240);
  return {
    p_idempotency_key: idempotencyKey,
    p_input_contract: 'nutrition-label.v1',
    p_source_document_ref: integrationRef(`label/${documentRef}`),
    p_food_name: name,
    p_brand: 'Integration Label Brand',
    p_category: 'processed',
    p_basis_amount: 100,
    p_basis_unit: 'g',
    p_required_nutrients: values,
    p_nutrient_provenance: provenanceFor(
      values,
      REQUIRED_NUTRIENTS.map(() => 'product_label_ocr'),
      'observed',
      `label/${documentRef}`
    ),
    p_optional_nutrients: { fiber_grams: 4.2 },
    p_provenance: { reviewed_in: 'integration-test' },
    p_user_verified: true
  };
}

function estimatePayload(idempotencyKey, name, documentRef = idempotencyKey) {
  const values = requiredValues(680);
  const sourceTypes = [
    'food_image_estimate',
    'food_image_estimate',
    'food_image_estimate',
    'food_image_estimate',
    'manual',
    'food_image_estimate',
    'menu_reference'
  ];
  const range = Object.fromEntries(REQUIRED_NUTRIENTS.map((key) => ({
    [key]: {
      min: Math.max(0, values[key] * 0.8),
      point: values[key],
      max: values[key] * 1.25
    }
  })));
  return {
    p_idempotency_key: idempotencyKey,
    p_input_contract: 'food-estimate.v1',
    p_source_document_ref: integrationRef(`estimate/${documentRef}`),
    p_food_name: name,
    p_brand: 'Integration Restaurant',
    p_category: 'recipe',
    p_basis_amount: 1,
    p_basis_unit: 'serving',
    p_required_nutrients: values,
    p_nutrient_provenance: provenanceFor(values, sourceTypes, 'estimated', `estimate/${documentRef}`),
    p_optional_nutrients: {},
    p_provenance: { restaurant_name: 'Integration Restaurant', estimated: true },
    p_user_verified: true,
    p_estimation_evidence: { confidence: 0.72, range }
  };
}

function legacyLabelPayload(idempotencyKey, name) {
  const values = requiredValues(125);
  return {
    p_idempotency_key: idempotencyKey,
    p_source_document_ref: integrationRef(`legacy/${idempotencyKey}`),
    p_evidence_type: 'product_label',
    p_food_name: name,
    p_brand: 'Integration Legacy Brand',
    p_category: 'processed',
    p_basis_amount: 100,
    p_basis_unit: 'g',
    p_required_nutrients: values,
    p_optional_nutrients: {},
    p_provenance: { reviewed_in: 'integration-test' },
    p_user_verified: true
  };
}

async function getRows(owner, table, filters, select = '*') {
  const query = new URLSearchParams({ select });
  for (const [column, value] of Object.entries(filters)) query.set(column, `eq.${value}`);
  return expectOk(
    `select ${table}`,
    `/rest/v1/${table}?${query.toString()}`,
    { method: 'GET', headers: rpcHeaders(owner) }
  );
}

function recordImport(result) {
  created.canonicalImportIds.add(result.canonical_import_id);
  created.foodIds.add(result.nutrition_food_id);
  created.projectionImportIds.add(result.projection_import_id);
  if (result.estimation_evidence_id) created.estimationEvidenceIds.add(result.estimation_evidence_id);
}

function recordLegacy(result) {
  created.foodIds.add(result.nutrition_food_id);
  created.projectionImportIds.add(result.import_id);
  if (result.estimation_evidence_id) created.estimationEvidenceIds.add(result.estimation_evidence_id);
}

async function verifyCanonicalResult(owner, result, contract, sourceType) {
  assert(result.canonical_import_id, 'canonical result is missing canonical_import_id');
  assertEqual(result.idempotent_replay, false, 'first canonical import must not be a replay');
  assertEqual(result.input_contract, contract, 'canonical contract');
  assertEqual(result.projection_source_type, sourceType, 'projection source type');
  assertEqual(result.visibility, 'private', 'projection visibility');
  recordImport(result);

  const imports = await getRows(owner, 'nutrition_canonical_imports', {
    id: result.canonical_import_id
  });
  assertEqual(imports.length, 1, 'owner can read its canonical import');
  assertEqual(imports[0].owner_id, owner.userId, 'canonical import owner');

  const foods = await getRows(owner, 'nutrition_foods', { id: result.nutrition_food_id });
  assertEqual(foods.length, 1, 'owner can read its private food projection');
  assertEqual(foods[0].owner_id, owner.userId, 'food projection owner');
  assertEqual(foods[0].source_type, sourceType, 'food projection source type');
  assertEqual(foods[0].visibility, 'private', 'food projection visibility');

  const provenance = await getRows(
    owner,
    'nutrition_food_nutrient_provenance',
    { canonical_import_id: result.canonical_import_id },
    'nutrient_code,value,value_status,source_type,evidence_refs,confidence,uncertainty_range'
  );
  assertEqual(provenance.length, 7, 'canonical import must persist exactly seven provenance rows');
  const nutrientCodes = provenance.map((row) => row.nutrient_code).sort();
  assertEqual(nutrientCodes.join('|'), [...REQUIRED_NUTRIENTS].sort().join('|'), 'provenance nutrient keys');
  for (const row of provenance) {
    assert(row.evidence_refs?.length > 0, `evidence refs for ${row.nutrient_code}`);
    assert(row.value_status === 'observed' || row.value_status === 'estimated', `value status for ${row.nutrient_code}`);
  }
  console.log(`PASS ${contract} import + seven provenance rows`);
}

async function verifyOwnerIsolation(ownerA, ownerB, result) {
  const otherImportRows = await getRows(ownerB, 'nutrition_canonical_imports', {
    id: result.canonical_import_id
  });
  assertEqual(otherImportRows.length, 0, 'other owner cannot read canonical import');
  const otherFoodRows = await getRows(ownerB, 'nutrition_foods', { id: result.nutrition_food_id });
  assertEqual(otherFoodRows.length, 0, 'other owner cannot read private food');
  const otherProvenanceRows = await getRows(ownerB, 'nutrition_food_nutrient_provenance', {
    canonical_import_id: result.canonical_import_id
  });
  assertEqual(otherProvenanceRows.length, 0, 'other owner cannot read provenance');
  console.log('PASS owner isolation for food, canonical import, and provenance');
}

async function directWriteChecks(owner, canonicalResult) {
  const foodBase = {
    id: crypto.randomUUID(),
    owner_id: owner.userId,
    name: 'Direct bypass probe',
    kind: 'ingredient',
    basis_amount: 100,
    basis_unit: 'g',
    calories_kcal: 100,
    carbs_grams: 10,
    protein_grams: 10,
    fat_grams: 3,
    sodium_mg: 100,
    saturated_fat_grams: 1,
    sugars_grams: 1,
    data_version: 2,
    visibility: 'private'
  };
  for (const sourceType of ['product_label', 'product_label_ocr', 'food_image_estimate']) {
    await expectRejected(
      `direct nutrition_foods ${sourceType} bypass`,
      '/rest/v1/nutrition_foods',
      {
        method: 'POST',
        headers: { ...rpcHeaders(owner), Prefer: 'return=representation' },
        body: JSON.stringify({ ...foodBase, id: crypto.randomUUID(), source_type: sourceType })
      }
    );
  }

  const manualFoodId = crypto.randomUUID();
  const manualBody = await expectOk(
    'direct manual nutrition_foods write remains available',
    '/rest/v1/nutrition_foods',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=representation' },
      body: JSON.stringify({ ...foodBase, id: manualFoodId, source_type: 'manual' })
    }
  );
  assert(Array.isArray(manualBody) && manualBody.length === 1, 'manual direct write must return one row');
  created.foodIds.add(manualFoodId);
  console.log('PASS manual nutrition_foods write remains available');

  await expectRejected(
    'direct canonical audit insert is denied',
    '/rest/v1/nutrition_canonical_imports',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        owner_id: owner.userId,
        input_contract: 'nutrition-label.v1',
        idempotency_key: uniqueId('direct-audit'),
        source_document_ref: integrationRef('direct-audit'),
        user_verified: true,
        required_nutrients: {},
        optional_nutrients: {},
        nutrient_provenance: {},
        provenance: {},
        request_payload: {},
        projection_import_id: canonicalResult.projection_import_id,
        nutrition_food_id: canonicalResult.nutrition_food_id,
        projection_source_type: 'product_label_ocr'
      })
    }
  );
  await expectRejected(
    'direct nutrient provenance insert is denied',
    '/rest/v1/nutrition_food_nutrient_provenance',
    {
      method: 'POST',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        owner_id: owner.userId,
        nutrition_food_id: canonicalResult.nutrition_food_id,
        canonical_import_id: canonicalResult.canonical_import_id,
        nutrient_code: 'calories_kcal',
        value: 1,
        value_status: 'observed',
        source_type: 'product_label_ocr',
        evidence_refs: [integrationRef('direct-provenance')]
      })
    }
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
        headers: { ...authHeaders(null, serviceRoleKey), Prefer: 'return=minimal' }
      }
    );
    if (!result.ok) throw new ApiError(`cleanup ${table}`, result.status, result.body);
  }
}

async function cleanup() {
  if (!serviceRoleKey) return;
  await cleanupTable('nutrition_food_nutrient_provenance', 'canonical_import_id', created.canonicalImportIds);
  await cleanupTable('nutrition_canonical_imports', 'id', created.canonicalImportIds);
  await cleanupTable('nutrition_estimation_evidence', 'id', created.estimationEvidenceIds);
  await cleanupTable('nutrition_verified_imports', 'id', created.projectionImportIds);
  await cleanupTable('nutrition_verified_catalog_keys', 'nutrition_food_id', created.foodIds);
  await cleanupTable('nutrition_foods', 'id', created.foodIds);

  for (const userId of created.userIds) {
    const result = await request(
      `cleanup auth user ${userId}`,
      `/auth/v1/admin/users/${encodeURIComponent(userId)}`,
      { method: 'DELETE', headers: authHeaders(null, serviceRoleKey) }
    );
    if (!result.ok) throw new ApiError('cleanup auth user', result.status, result.body);
  }
  console.log('PASS integration cleanup');
}

async function run() {
  if (process.env.NUTRITION_INTEGRATION_ALLOW_REMOTE !== 'true') {
    throw new ConfigurationError(
      'Set NUTRITION_INTEGRATION_ALLOW_REMOTE=true to run this real Supabase/Postgres integration test.'
    );
  }
  if (!baseUrl || !anonKey) {
    throw new ConfigurationError(
      'NUTRITION_DB_URL/NUTRITION_DB_ANON (or the documented Supabase aliases) are required.'
    );
  }

  const { ownerA, ownerB } = await resolveOwners();
  const label = labelPayload(uniqueId('label'), 'Integration Label Food');
  const labelResult = await callCanonical(ownerA, label);
  await verifyCanonicalResult(ownerA, labelResult, 'nutrition-label.v1', 'product_label_ocr');

  const estimate = estimatePayload(uniqueId('estimate'), 'Integration Estimated Menu');
  const estimateResult = await callCanonical(ownerA, estimate);
  await verifyCanonicalResult(ownerA, estimateResult, 'food-estimate.v1', 'food_image_estimate');
  await verifyOwnerIsolation(ownerA, ownerB, labelResult);

  const replay = await callCanonical(ownerA, label);
  assertEqual(replay.canonical_import_id, labelResult.canonical_import_id, 'idempotent replay id');
  assertEqual(replay.nutrition_food_id, labelResult.nutrition_food_id, 'idempotent replay food id');
  assertEqual(replay.idempotent_replay, true, 'idempotent replay flag');
  assertEqual((await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
    canonical_import_id: labelResult.canonical_import_id
  })).length, 7, 'idempotent replay must not add provenance rows');
  console.log('PASS idempotency replay');

  const collision = { ...label, p_food_name: 'Integration Label Collision' };
  await assertFunctionRejected('idempotency collision', () => callCanonical(ownerA, collision));

  const sharedKey = uniqueId('shared-v1-v2-key');
  const legacyResult = await callLegacy(ownerA, legacyLabelPayload(sharedKey, 'Integration Legacy Food'));
  recordLegacy(legacyResult);
  const v2SharedResult = await callCanonical(ownerA, labelPayload(sharedKey, 'Integration V2 Same Key'));
  recordImport(v2SharedResult);
  assert(legacyResult.import_id !== v2SharedResult.projection_import_id, 'v1/v2 projection ids must be namespaced');
  assert(legacyResult.nutrition_food_id !== v2SharedResult.nutrition_food_id, 'v1/v2 collision fixture must remain independent');
  console.log('PASS v1/v2 shared idempotency key namespace separation');

  const extraRequired = labelPayload(uniqueId('extra-required'), 'Integration Extra Required');
  extraRequired.p_required_nutrients = { ...extraRequired.p_required_nutrients, extra_key: 1 };
  await assertFunctionRejected('extra required nutrient key rejection', () => callCanonical(ownerA, extraRequired));

  const missingProvenance = labelPayload(uniqueId('missing-provenance'), 'Integration Missing Provenance');
  delete missingProvenance.p_nutrient_provenance.sodium_mg;
  await assertFunctionRejected('missing provenance nutrient key rejection', () => callCanonical(ownerA, missingProvenance));

  await assertFunctionRejected('anonymous canonical RPC rejection', async () => {
    await expectOk(
      'anonymous canonical RPC',
      '/rest/v1/rpc/import_canonical_nutrition_v2',
      {
        method: 'POST',
        headers: authHeaders(null),
        body: JSON.stringify(labelPayload(uniqueId('anonymous'), 'Anonymous Import'))
      }
    );
  });
  await expectRejected(
    'anonymous canonical audit read rejection',
    '/rest/v1/nutrition_canonical_imports?select=id',
    { method: 'GET', headers: authHeaders(null) }
  );

  await directWriteChecks(ownerA, labelResult);
  console.log('PASS all canonical Nutrition integration checks');
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
