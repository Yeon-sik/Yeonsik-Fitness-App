import crypto from 'node:crypto';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import dotenv from 'dotenv';

const integrationDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(integrationDirectory, '../../..');
const EXTERNAL_REFERENCE_FIXTURE = JSON.parse(readFileSync(
  path.join(repositoryRoot, 'contracts', 'fitness-external-reference.v1.json'),
  'utf8'
));
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

const EXPLICIT_HIERARCHY_FIELDS = [
  'p_manufacturer_name',
  'p_brand_name',
  'p_sub_brand_name',
  'p_product_name'
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

async function callCanonicalV3(owner, payload) {
  assertAuthoritativeV3RequestShape(payload);
  const body = await expectOk(
    `canonical v3 import ${payload.p_idempotency_key}`,
    '/rest/v1/rpc/import_canonical_nutrition_v3',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload)
    }
  );
  assert(Array.isArray(body) && body.length === 1, 'canonical v3 import must return exactly one row');
  return body[0];
}

async function callOcrDiningOutPublication(owner, payload) {
  const body = await expectOk(
    `OCR dining-out publication ${payload.p_idempotency_key}`,
    '/rest/v1/rpc/publish_verified_ocr_dining_out_nutrition_v1',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload)
    }
  );
  assert(Array.isArray(body) && body.length === 1, 'OCR publication must return exactly one row');
  return body[0];
}

function exactDiningOutIdentity() {
  return {
    schema_version: 'dining-out-identity.v1',
    namespace: 'pricetrace',
    restaurant_id: crypto.randomUUID(),
    restaurant_location_id: crypto.randomUUID(),
    restaurant_menu_id: crypto.randomUUID(),
    catalog_product_id: crypto.randomUUID()
  };
}

function assertAuthoritativeV3RequestShape(payload) {
  assert(
    !Object.prototype.hasOwnProperty.call(payload, 'p_category_hierarchy'),
    'v3 request must not use p_category_hierarchy'
  );
  assert(
    payload.p_input_contract === 'nutrition-label.v1'
      || payload.p_input_contract === 'food-estimate.v1'
      || payload.p_input_contract === 'external-reference.v1',
    'v3 endpoint must accept only the documented nutrient input contracts'
  );
  for (const field of EXPLICIT_HIERARCHY_FIELDS) {
    assert(
      Object.prototype.hasOwnProperty.call(payload, field),
      `v3 request must include explicit nullable field ${field}`
    );
  }
}

function hierarchyFingerprint(values) {
  // PostgreSQL hashes jsonb_build_array(... )::text, which inserts a space
  // after each comma; mirror that canonical text instead of compact JSON.stringify.
  const postgresJsonbArrayText = `[${values.map(value => JSON.stringify(value)).join(', ')}]`;
  return crypto.createHash('sha256')
    .update(postgresJsonbArrayText, 'utf8')
    .digest('hex');
}

async function callNutritionReadV3(owner, query) {
  const body = await expectOk(
    `nutrition v3 read ${query}`,
    '/rest/v1/rpc/get_nutrition_read_v3',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify({ p_query: query })
    }
  );
  assert(Array.isArray(body) && body.length === 1, 'nutrition v3 read must return exactly one row');
  return body[0];
}

async function verifyV3ReadIsolation(owner, query) {
  const body = await expectOk(
    `nutrition v3 private read isolation ${query}`,
    '/rest/v1/rpc/get_nutrition_read_v3',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify({ p_query: query })
    }
  );
  assert(Array.isArray(body) && body.length === 0, 'other owner must not read private v3 nutrition');
  console.log('PASS v3 read RLS owner isolation');
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
  return Object.fromEntries(REQUIRED_NUTRIENTS.map((key, index) => [
    key,
    {
      value: values[key],
      value_status: valueStatus,
      source_type: sourceTypes[index],
      evidence_refs: [integrationRef(`${refPrefix}/${key}`)]
    }
  ]));
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

function packagedHierarchyLabelPayload(idempotencyKey, name, documentRef = idempotencyKey) {
  return {
    ...labelPayload(idempotencyKey, name, documentRef),
    p_brand: 'Integration Hierarchy Brand',
    p_manufacturer_name: 'Integration Manufacturer',
    p_brand_name: 'Integration Hierarchy Brand',
    p_sub_brand_name: 'Integration Sub Brand',
    p_product_name: 'Integration Canonical Product',
    p_pricetrace_identity: {
      namespace: 'pricetrace',
      catalog_product_id: crypto.randomUUID()
    }
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
  const range = Object.fromEntries(REQUIRED_NUTRIENTS.map((key) => [
    key,
    {
      min: Math.max(0, values[key] * 0.8),
      point: values[key],
      max: values[key] * 1.25
    }
  ]));
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

function estimateV3Payload(idempotencyKey, name, documentRef = idempotencyKey) {
  return {
    ...estimatePayload(idempotencyKey, name, documentRef),
    p_manufacturer_name: null,
    p_brand_name: null,
    p_sub_brand_name: null,
    p_product_name: null
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

async function verifyExternalReference(owner, result, payload, readQuery) {
  await verifyCanonicalResult(owner, result, 'external-reference.v1', 'external_reference');

  const imports = await getRows(
    owner,
    'nutrition_canonical_imports',
    { id: result.canonical_import_id },
    'request_payload,provenance'
  );
  assertEqual(imports.length, 1, 'external canonical audit row');
  assertEqual(imports[0].request_payload.input_contract, 'external-reference.v1', 'external audit contract');
  assertEqual(imports[0].request_payload.source_document_ref, payload.p_source_document_ref, 'external audit URL');
  assertEqual(imports[0].provenance.source_type, 'external_reference', 'external audit source type');
  assertEqual(imports[0].provenance.source_reference, payload.p_source_document_ref, 'external audit source URL');
  assertEqual(imports[0].provenance.source_version, 'external-nutrition-lookup.v1', 'external audit source version');

  const foods = await getRows(
    owner,
    'nutrition_foods',
    { id: result.nutrition_food_id },
    'source_type,source_reference,source_version'
  );
  assertEqual(foods.length, 1, 'external source food row');
  assertEqual(foods[0].source_type, 'external_reference', 'external food source type');
  assertEqual(foods[0].source_reference, payload.p_source_document_ref, 'external food source URL');
  assertEqual(foods[0].source_version, 'external-nutrition-lookup.v1', 'external food source version');

  const provenance = await getRows(
    owner,
    'nutrition_food_nutrient_provenance',
    { canonical_import_id: result.canonical_import_id },
    'source_type,value_status,evidence_refs'
  );
  for (const row of provenance) {
    assertEqual(row.source_type, 'external_reference', 'external nutrient source type');
    assertEqual(row.value_status, 'observed', 'external nutrient value status');
    assert(row.evidence_refs.includes(payload.p_source_document_ref), 'external nutrient source URL evidence');
  }

  const read = await callNutritionReadV3(owner, readQuery);
  assertEqual(read.source_type, 'external_reference', 'external read source type');
  assertEqual(read.source_reference, payload.p_source_document_ref, 'external read source URL');
  assertEqual(read.source_revision, 'external-nutrition-lookup.v1', 'external read source revision');
  console.log('PASS external-reference.v1 provenance survives canonical and read projections');
}

async function verifyV3Hierarchy(owner, result, expected, readQuery) {
  assertEqual(result.manufacturer_name, expected.manufacturer_name, 'v3 result manufacturer');
  assertEqual(result.brand_name, expected.brand_name, 'v3 result brand');
  assertEqual(result.sub_brand_name, expected.sub_brand_name, 'v3 result sub-brand');
  assertEqual(result.product_name, expected.product_name, 'v3 result product');

  const imports = await getRows(
    owner,
    'nutrition_canonical_imports',
    { id: result.canonical_import_id },
    'request_payload'
  );
  assertEqual(imports.length, 1, 'v3 canonical audit row');
  assertEqual(imports[0].request_payload.manufacturer_name, expected.manufacturer_name, 'audited manufacturer');
  assertEqual(imports[0].request_payload.brand_name, expected.brand_name, 'audited brand');
  assertEqual(imports[0].request_payload.sub_brand_name, expected.sub_brand_name, 'audited sub-brand');
  assertEqual(imports[0].request_payload.product_name, expected.product_name, 'audited product');
  assertEqual(
    imports[0].request_payload.hierarchy_fingerprint,
    hierarchyFingerprint([
      expected.manufacturer_name,
      expected.brand_name,
      expected.sub_brand_name,
      expected.product_name
    ]),
    'audited ordered hierarchy fingerprint'
  );

  const foods = await getRows(
    owner,
    'nutrition_foods',
    { id: result.nutrition_food_id },
    'brand,manufacturer_name,brand_name,sub_brand_name,product_name,source_version'
  );
  assertEqual(foods.length, 1, 'v3 hierarchy food row');
  assertEqual(foods[0].brand, expected.legacy_brand, 'legacy brand compatibility');
  assertEqual(foods[0].manufacturer_name, expected.manufacturer_name, 'stored manufacturer');
  assertEqual(foods[0].brand_name, expected.brand_name, 'stored brand');
  assertEqual(foods[0].sub_brand_name, expected.sub_brand_name, 'stored sub-brand');
  assertEqual(foods[0].product_name, expected.product_name, 'stored product');
  assertEqual(foods[0].source_version, 'nutrition-projection.v3', 'v3 projection source version');

  const read = await callNutritionReadV3(owner, readQuery);
  assertEqual(read.contract_version, 'nutrition-read.v3', 'v3 read contract');
  assertEqual(read.nutrition_food_id, result.nutrition_food_id, 'v3 read food id');
  assertEqual(read.brand, expected.legacy_brand, 'v3 read legacy brand');
  assertEqual(read.manufacturer_name, expected.manufacturer_name, 'v3 read manufacturer');
  assertEqual(read.brand_name, expected.brand_name, 'v3 read brand');
  assertEqual(read.sub_brand_name, expected.sub_brand_name, 'v3 read sub-brand');
  assertEqual(read.product_name, expected.product_name, 'v3 read product');

  const links = await getRows(
    owner,
    'product_nutrition_links',
    { nutrition_food_id: result.nutrition_food_id },
    'catalog_product_id,status,product_contract_version'
  );
  assertEqual(links.length, 1, 'v3 exact product link');
  assertEqual(links[0].catalog_product_id, expected.catalog_product_id, 'v3 linked catalog product');
  assertEqual(links[0].status, 'approved', 'v3 product link status');
  assertEqual(links[0].product_contract_version, 'product-read.v1', 'v3 product link contract');
  console.log('PASS v3 hierarchy storage, read round-trip, and product link');
}

async function verifyV3NullHierarchy(owner, result, expectedLegacyBrand, readQuery) {
  const imports = await getRows(
    owner,
    'nutrition_canonical_imports',
    { id: result.canonical_import_id },
    'request_payload'
  );
  assertEqual(imports.length, 1, 'v3 partial canonical audit row');
  assertEqual(imports[0].request_payload.manufacturer_name, null, 'audited null manufacturer');
  assertEqual(imports[0].request_payload.brand_name, null, 'audited null brand');
  assertEqual(imports[0].request_payload.sub_brand_name, null, 'audited null sub-brand');
  assertEqual(imports[0].request_payload.product_name, null, 'audited null product');
  assertEqual(
    imports[0].request_payload.hierarchy_fingerprint,
    hierarchyFingerprint([null, null, null, null]),
    'audited all-null hierarchy fingerprint'
  );

  const foods = await getRows(
    owner,
    'nutrition_foods',
    { id: result.nutrition_food_id },
    'brand,manufacturer_name,brand_name,sub_brand_name,product_name'
  );
  assertEqual(foods.length, 1, 'v3 partial hierarchy food row');
  assertEqual(foods[0].brand, expectedLegacyBrand, 'partial legacy brand compatibility');
  assertEqual(foods[0].manufacturer_name, null, 'blank manufacturer becomes null');
  assertEqual(foods[0].brand_name, null, 'blank brand becomes null');
  assertEqual(foods[0].sub_brand_name, null, 'blank sub-brand becomes null');
  assertEqual(foods[0].product_name, null, 'blank product becomes null');
  assertEqual(result.manufacturer_name, null, 'partial result manufacturer');
  assertEqual(result.brand_name, null, 'partial result brand');
  assertEqual(result.sub_brand_name, null, 'partial result sub-brand');
  assertEqual(result.product_name, null, 'partial result product');
  const read = await callNutritionReadV3(owner, readQuery);
  assertEqual(read.nutrition_food_id, result.nutrition_food_id, 'null hierarchy read food id');
  assertEqual(read.manufacturer_name, null, 'null hierarchy read manufacturer');
  assertEqual(read.brand_name, null, 'null hierarchy read brand');
  assertEqual(read.sub_brand_name, null, 'null hierarchy read sub-brand');
  assertEqual(read.product_name, null, 'null hierarchy read product');
  console.log('PASS v3 partial hierarchy blank-to-null behavior');
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
  for (const sourceType of ['product_label', 'product_label_ocr', 'food_image_estimate', 'external_reference']) {
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
  await expectRejected(
    'direct canonical nutrition_foods update is denied',
    `/rest/v1/nutrition_foods?id=eq.${encodeURIComponent(canonicalResult.nutrition_food_id)}`,
    {
      method: 'PATCH',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' },
      body: JSON.stringify({ calories_kcal: 9999 })
    }
  );
  await expectRejected(
    'direct canonical nutrition_foods delete is denied',
    `/rest/v1/nutrition_foods?id=eq.${encodeURIComponent(canonicalResult.nutrition_food_id)}`,
    {
      method: 'DELETE',
      headers: { ...rpcHeaders(owner), Prefer: 'return=minimal' }
    }
  );

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
  await cleanupTable('nutrition_ocr_dining_out_publications', 'nutrition_food_id', created.foodIds);
  await cleanupTable('nutrition_dining_out_publication_events', 'nutrition_food_id', created.foodIds);
  await cleanupTable('product_nutrition_links', 'nutrition_food_id', created.foodIds);
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

async function runExternalReferenceOnly(ownerA, ownerB) {
  const payload = {
    ...JSON.parse(JSON.stringify(EXTERNAL_REFERENCE_FIXTURE)),
    p_idempotency_key: uniqueId('external-reference-replay')
  };
  const result = await callCanonicalV3(ownerA, payload);
  recordImport(result);
  await verifyExternalReference(ownerA, result, payload, payload.p_food_name);
  await verifyOwnerIsolation(ownerA, ownerB, result);
  await verifyV3ReadIsolation(ownerB, payload.p_food_name);

  const replay = await callCanonicalV3(ownerA, payload);
  recordImport(replay);
  assertEqual(replay.canonical_import_id, result.canonical_import_id, 'external replay canonical id');
  assertEqual(replay.nutrition_food_id, result.nutrition_food_id, 'external replay food id');
  assertEqual(replay.idempotent_replay, true, 'external replay flag');
  assertEqual(
    (await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
      canonical_import_id: result.canonical_import_id
    })).length,
    7,
    'external replay must not add provenance rows'
  );

  const changedPayload = {
    ...JSON.parse(JSON.stringify(payload)),
    p_food_name: `${payload.p_food_name} changed`
  };
  await assertFunctionRejected(
    'external-reference same-key changed-payload conflict',
    () => callCanonicalV3(ownerA, changedPayload)
  );

  const missingNutrientProvenance = {
    ...JSON.parse(JSON.stringify(payload)),
    p_idempotency_key: uniqueId('external-reference-missing-provenance')
  };
  delete missingNutrientProvenance.p_nutrient_provenance.sodium_mg;
  await assertFunctionRejected(
    'external-reference missing nutrient provenance rejection',
    () => callCanonicalV3(ownerA, missingNutrientProvenance)
  );

  const changedEvidenceReference = {
    ...JSON.parse(JSON.stringify(payload)),
    p_idempotency_key: uniqueId('external-reference-evidence-mismatch')
  };
  changedEvidenceReference.p_nutrient_provenance.calories_kcal.evidence_refs = [
    'https://example.test/different-source'
  ];
  await assertFunctionRejected(
    'external-reference nutrient source URL evidence rejection',
    () => callCanonicalV3(ownerA, changedEvidenceReference)
  );

  await expectRejected(
    'anonymous external-reference canonical RPC rejection',
    '/rest/v1/rpc/import_canonical_nutrition_v3',
    {
      method: 'POST',
      headers: authHeaders(null),
      body: JSON.stringify(payload)
    }
  );
  console.log('PASS external-reference private import, owner isolation, seven nutrient provenance, source URL, auth, and idempotency checks');
}

async function reproduceGenericV3NutritionFoodIdAmbiguity(owner) {
  const payload = packagedHierarchyLabelPayload(
    uniqueId('generic-v3-ambiguity-repro'),
    'Generic v3 Ambiguity Reproduction'
  );
  const result = await request(
    'generic v3 ambiguity reproduction',
    '/rest/v1/rpc/import_canonical_nutrition_v3',
    {
      method: 'POST',
      headers: rpcHeaders(owner),
      body: JSON.stringify(payload)
    }
  );
  assert(!result.ok, 'generic v3 ambiguity reproduction unexpectedly succeeded');
  assertEqual(result.body?.code, '42702', 'generic v3 ambiguity SQLSTATE');
  assert(
    /nutrition_food_id/i.test(result.body?.message || ''),
    `generic v3 ambiguity error did not identify nutrition_food_id: ${bodyJson(result.body)}`
  );
  console.log('PASS reproduced authenticated generic v3 nutrition_food_id ambiguity (SQLSTATE 42702)');
  console.log(`DIAG generic-v3-ambiguity ${bodyJson(result.body)}`);
}

async function runCanonicalV3ContractMatrix(ownerA, ownerB) {
  const label = packagedHierarchyLabelPayload(
    uniqueId('v3-matrix-label'),
    'Integration v3 Label Matrix'
  );
  const labelResult = await callCanonicalV3(ownerA, label);
  await verifyCanonicalResult(ownerA, labelResult, 'nutrition-label.v1', 'product_label_ocr');
  await verifyV3Hierarchy(
    ownerA,
    labelResult,
    {
      manufacturer_name: label.p_manufacturer_name,
      brand_name: label.p_brand_name,
      sub_brand_name: label.p_sub_brand_name,
      product_name: label.p_product_name,
      legacy_brand: label.p_brand,
      catalog_product_id: label.p_pricetrace_identity.catalog_product_id
    },
    label.p_sub_brand_name
  );
  await verifyOwnerIsolation(ownerA, ownerB, labelResult);
  await verifyV3ReadIsolation(ownerB, label.p_sub_brand_name);
  const labelReplay = await callCanonicalV3(ownerA, label);
  assertEqual(labelReplay.canonical_import_id, labelResult.canonical_import_id, 'label v3 replay import id');
  assertEqual(labelReplay.nutrition_food_id, labelResult.nutrition_food_id, 'label v3 replay food id');
  assertEqual(labelReplay.idempotent_replay, true, 'label v3 replay flag');
  assertEqual(
    (await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
      canonical_import_id: labelResult.canonical_import_id
    })).length,
    7,
    'label v3 replay must not add provenance rows'
  );
  console.log('PASS nutrition-label.v1 v3 owner isolation and idempotency');

  const estimate = estimateV3Payload(
    uniqueId('v3-matrix-estimate'),
    'Integration v3 Estimate Matrix'
  );
  const estimateResult = await callCanonicalV3(ownerA, estimate);
  await verifyCanonicalResult(ownerA, estimateResult, 'food-estimate.v1', 'food_image_estimate');
  await verifyV3NullHierarchy(ownerA, estimateResult, estimate.p_brand, estimate.p_food_name);
  await verifyOwnerIsolation(ownerA, ownerB, estimateResult);
  await verifyV3ReadIsolation(ownerB, estimate.p_food_name);
  const estimateProvenance = await getRows(
    ownerA,
    'nutrition_food_nutrient_provenance',
    { canonical_import_id: estimateResult.canonical_import_id },
    'nutrient_code,value_status,source_type,confidence,uncertainty_range,evidence_refs'
  );
  assertEqual(estimateProvenance.length, 7, 'estimate v3 provenance row count');
  for (const row of estimateProvenance) {
    assertEqual(row.value_status, 'estimated', `estimate status for ${row.nutrient_code}`);
    assertEqual(row.confidence, estimate.p_estimation_evidence.confidence, `estimate confidence for ${row.nutrient_code}`);
    const expectedRange = estimate.p_estimation_evidence.range[row.nutrient_code];
    assert(expectedRange, `estimate payload range for ${row.nutrient_code}`);
    assert(row.uncertainty_range, `persisted estimate uncertainty range for ${row.nutrient_code}`);
    for (const bound of ['min', 'point', 'max']) {
      assertEqual(
        row.uncertainty_range[bound],
        expectedRange[bound],
        `estimate ${bound} provenance for ${row.nutrient_code}`
      );
    }
  }
  const estimateReplay = await callCanonicalV3(ownerA, estimate);
  assertEqual(estimateReplay.canonical_import_id, estimateResult.canonical_import_id, 'estimate v3 replay import id');
  assertEqual(estimateReplay.nutrition_food_id, estimateResult.nutrition_food_id, 'estimate v3 replay food id');
  assertEqual(estimateReplay.idempotent_replay, true, 'estimate v3 replay flag');
  assertEqual(
    (await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
      canonical_import_id: estimateResult.canonical_import_id
    })).length,
    7,
    'estimate v3 replay must not add provenance rows'
  );
  console.log('PASS food-estimate.v1 v3 owner isolation, uncertainty provenance, and idempotency');

  const externalReference = {
    ...JSON.parse(JSON.stringify(EXTERNAL_REFERENCE_FIXTURE)),
    p_idempotency_key: uniqueId('v3-matrix-external-reference')
  };
  const externalResult = await callCanonicalV3(ownerA, externalReference);
  await verifyExternalReference(
    ownerA,
    externalResult,
    externalReference,
    externalReference.p_food_name
  );
  await verifyOwnerIsolation(ownerA, ownerB, externalResult);
  await verifyV3ReadIsolation(ownerB, externalReference.p_food_name);
  const externalReplay = await callCanonicalV3(ownerA, externalReference);
  assertEqual(externalReplay.canonical_import_id, externalResult.canonical_import_id, 'external v3 replay import id');
  assertEqual(externalReplay.nutrition_food_id, externalResult.nutrition_food_id, 'external v3 replay food id');
  assertEqual(externalReplay.idempotent_replay, true, 'external v3 replay flag');
  assertEqual(
    (await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
      canonical_import_id: externalResult.canonical_import_id
    })).length,
    7,
    'external v3 replay must not add provenance rows'
  );
  console.log('PASS external-reference.v1 v3 owner isolation, source reference, provenance, and idempotency');

  for (const [contract, payload] of [
    ['nutrition-label.v1', label],
    ['food-estimate.v1', estimate],
    ['external-reference.v1', externalReference]
  ]) {
    const anonymous = await request(
      `anonymous canonical v3 ${contract}`,
      '/rest/v1/rpc/import_canonical_nutrition_v3',
      {
        method: 'POST',
        headers: authHeaders(null),
        body: JSON.stringify(payload)
      }
    );
    assert(!anonymous.ok, `anonymous ${contract} import unexpectedly succeeded`);
    assertEqual(anonymous.status, 401, `anonymous ${contract} status`);
    console.log(`PASS anonymous ${contract} v3 rejection (401)`);
  }
  console.log('PASS all three canonical v3 contract paths');
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
  if (process.env.NUTRITION_INTEGRATION_MODE === 'generic-v3-ambiguity-repro') {
    await reproduceGenericV3NutritionFoodIdAmbiguity(ownerA);
    return;
  }
  if (process.env.NUTRITION_INTEGRATION_MODE === 'canonical-v3-contract-matrix') {
    await runCanonicalV3ContractMatrix(ownerA, ownerB);
    return;
  }
  if (process.env.NUTRITION_INTEGRATION_MODE === 'external-reference-only') {
    await runExternalReferenceOnly(ownerA, ownerB);
    return;
  }

  const hierarchy = packagedHierarchyLabelPayload(
    uniqueId('hierarchy-full'),
    'Integration Hierarchy Display'
  );
  const hierarchyResult = await callCanonicalV3(ownerA, hierarchy);
  await verifyCanonicalResult(ownerA, hierarchyResult, 'nutrition-label.v1', 'product_label_ocr');
  await verifyV3Hierarchy(
    ownerA,
    hierarchyResult,
    {
      manufacturer_name: hierarchy.p_manufacturer_name,
      brand_name: hierarchy.p_brand_name,
      sub_brand_name: hierarchy.p_sub_brand_name,
      product_name: hierarchy.p_product_name,
      legacy_brand: hierarchy.p_brand,
      catalog_product_id: hierarchy.p_pricetrace_identity.catalog_product_id
    },
    hierarchy.p_sub_brand_name
  );
  await verifyOwnerIsolation(ownerA, ownerB, hierarchyResult);
  await verifyV3ReadIsolation(ownerB, hierarchy.p_sub_brand_name);

  const partialHierarchy = {
    ...labelPayload(uniqueId('hierarchy-partial'), 'Integration Partial Hierarchy'),
    p_brand: 'Integration Legacy Only Brand',
    p_manufacturer_name: '   ',
    p_brand_name: '',
    p_sub_brand_name: null,
    p_product_name: '  '
  };
  const partialHierarchyResult = await callCanonicalV3(ownerA, partialHierarchy);
  await verifyCanonicalResult(
    ownerA,
    partialHierarchyResult,
    'nutrition-label.v1',
    'product_label_ocr'
  );
  await verifyV3NullHierarchy(
    ownerA,
    partialHierarchyResult,
    partialHierarchy.p_brand,
    partialHierarchy.p_food_name
  );

  const estimateV3 = estimateV3Payload(uniqueId('estimate-v3'), 'Integration Estimated Menu v3');
  const estimateV3Result = await callCanonicalV3(ownerA, estimateV3);
  await verifyCanonicalResult(ownerA, estimateV3Result, 'food-estimate.v1', 'food_image_estimate');
  await verifyV3NullHierarchy(ownerA, estimateV3Result, estimateV3.p_brand, estimateV3.p_food_name);

  const publicationEstimate = estimateV3Payload(
    uniqueId('ocr-publication-import'),
    'Integration OCR Publication Menu'
  );
  const publicationImport = await callCanonicalV3(ownerA, publicationEstimate);
  await verifyCanonicalResult(ownerA, publicationImport, 'food-estimate.v1', 'food_image_estimate');
  const publicationIdentity = exactDiningOutIdentity();
  const publicationRequest = {
    p_idempotency_key: uniqueId('ocr-publication'),
    p_canonical_import_id: publicationImport.canonical_import_id,
    p_nutrition_food_id: publicationImport.nutrition_food_id,
    p_restaurant_id: publicationIdentity.restaurant_id,
    p_restaurant_location_id: publicationIdentity.restaurant_location_id,
    p_restaurant_menu_id: publicationIdentity.restaurant_menu_id,
    p_catalog_product_id: publicationIdentity.catalog_product_id
  };

  for (const field of [
    'p_restaurant_id',
    'p_restaurant_location_id',
    'p_restaurant_menu_id',
    'p_catalog_product_id'
  ]) {
    await assertFunctionRejected(
      `OCR publication rejects missing ${field}`,
      () => callOcrDiningOutPublication(ownerA, { ...publicationRequest, [field]: null })
    );
  }

  await assertFunctionRejected(
    'OCR publication rejects another owner Nutrition food',
    () => callOcrDiningOutPublication(ownerB, publicationRequest)
  );

  const unchangedBeforePublication = await getRows(
    ownerA,
    'nutrition_foods',
    { id: publicationImport.nutrition_food_id },
    'visibility,source_reference,publication_revision'
  );
  assertEqual(unchangedBeforePublication[0].visibility, 'private', 'rejected requests keep OCR Nutrition private');
  assertEqual(unchangedBeforePublication[0].publication_revision, 0, 'rejected requests do not publish');
  assertEqual(
    (await getRows(ownerA, 'product_nutrition_links', {
      nutrition_food_id: publicationImport.nutrition_food_id
    })).length,
    0,
    'rejected requests do not attach a Nutrition link'
  );
  assertEqual(
    (await getRows(ownerA, 'nutrition_dining_out_publication_events', {
      nutrition_food_id: publicationImport.nutrition_food_id
    })).length,
    0,
    'rejected requests do not create a publication event'
  );

  const publication = await callOcrDiningOutPublication(ownerA, publicationRequest);
  assertEqual(publication.canonical_import_id, publicationImport.canonical_import_id, 'publication provenance');
  assertEqual(publication.nutrition_food_id, publicationImport.nutrition_food_id, 'published Nutrition ID');
  assertEqual(publication.restaurant_id, publicationIdentity.restaurant_id, 'published restaurant ID');
  assertEqual(publication.restaurant_location_id, publicationIdentity.restaurant_location_id, 'published location ID');
  assertEqual(publication.restaurant_menu_id, publicationIdentity.restaurant_menu_id, 'published menu ID');
  assertEqual(publication.catalog_product_id, publicationIdentity.catalog_product_id, 'published catalog product ID');
  assert(publication.nutrition_link_id, 'publication result includes approved link ID');
  assert(publication.nutrition_link_revision >= 1, 'publication result includes link revision');
  assertEqual(publication.visibility, 'public', 'explicit OCR publication visibility');
  assert(publication.food_revision >= 1, 'publication result includes food revision');
  assert(publication.publication_revision >= 1, 'publication result includes publication revision');
  assert(publication.published_at, 'publication result includes published_at');
  assertEqual(publication.replayed, false, 'first OCR publication is not a replay');

  const publishedFood = await getRows(
    ownerA,
    'nutrition_foods',
    { id: publicationImport.nutrition_food_id },
    'visibility,source_reference,publication_revision,published_at'
  );
  assertEqual(publishedFood[0].visibility, 'public', 'published Nutrition row');
  assertEqual(publishedFood[0].publication_revision, publication.publication_revision, 'publication revision persisted');
  const attachedIdentity = JSON.parse(publishedFood[0].source_reference);
  for (const field of [
    'restaurant_id',
    'restaurant_location_id',
    'restaurant_menu_id',
    'catalog_product_id'
  ]) {
    assertEqual(attachedIdentity[field], publicationIdentity[field], `attached ${field}`);
  }
  const approvedLinks = await getRows(
    ownerA,
    'product_nutrition_links',
    { nutrition_food_id: publicationImport.nutrition_food_id, catalog_product_id: publicationIdentity.catalog_product_id },
    'nutrition_food_id,catalog_product_id,status,revision,deleted_at'
  );
  assertEqual(approvedLinks.length, 1, 'one approved exact Nutrition link');
  assertEqual(approvedLinks[0].status, 'approved', 'approved catalog/menu link');
  assertEqual(approvedLinks[0].deleted_at, null, 'approved catalog/menu link is active');
  const publicationEvents = await getRows(
    ownerA,
    'nutrition_dining_out_publication_events',
    { nutrition_food_id: publicationImport.nutrition_food_id },
    'action,restaurant_id,restaurant_location_id,restaurant_menu_id,catalog_product_id,publication_revision'
  );
  assertEqual(publicationEvents.length, 1, 'atomic RPC creates one publication event');
  assertEqual(publicationEvents[0].action, 'publish', 'publication event action');

  const publicationReplay = await callOcrDiningOutPublication(ownerA, publicationRequest);
  assertEqual(publicationReplay.replayed, true, 'identical OCR publication retry is replayed');
  assertEqual(publicationReplay.publication_revision, publication.publication_revision, 'replay publication revision');
  assertEqual(publicationReplay.published_at, publication.published_at, 'replay publication timestamp');
  const changedPublicationRequest = {
    ...publicationRequest,
    p_catalog_product_id: crypto.randomUUID()
  };
  await assertFunctionRejected(
    'OCR publication rejects changed payload under the same idempotency key',
    () => callOcrDiningOutPublication(ownerA, changedPublicationRequest)
  );

  const conflictingIdentity = exactDiningOutIdentity();
  const conflictingEstimate = {
    ...estimateV3Payload(uniqueId('ocr-publication-conflicting-identity'), 'Integration Conflicting Menu Identity'),
    p_pricetrace_identity: conflictingIdentity
  };
  const conflictingImport = await callCanonicalV3(ownerA, conflictingEstimate);
  await verifyCanonicalResult(ownerA, conflictingImport, 'food-estimate.v1', 'food_image_estimate');
  const mismatchedIdentityRequest = {
    p_idempotency_key: uniqueId('ocr-publication-mismatched-identity'),
    p_canonical_import_id: conflictingImport.canonical_import_id,
    p_nutrition_food_id: conflictingImport.nutrition_food_id,
    p_restaurant_id: conflictingIdentity.restaurant_id,
    p_restaurant_location_id: conflictingIdentity.restaurant_location_id,
    p_restaurant_menu_id: crypto.randomUUID(),
    p_catalog_product_id: conflictingIdentity.catalog_product_id
  };
  await assertFunctionRejected(
    'OCR publication rejects IDs that conflict with canonical PriceTrace provenance',
    () => callOcrDiningOutPublication(ownerA, mismatchedIdentityRequest)
  );
  const conflictingFood = await getRows(
    ownerA,
    'nutrition_foods',
    { id: conflictingImport.nutrition_food_id },
    'visibility,publication_revision'
  );
  assertEqual(conflictingFood[0].visibility, 'private', 'identity conflict keeps Nutrition private');
  assertEqual(conflictingFood[0].publication_revision, 0, 'identity conflict does not publish');

  const legacyExactIdentity = exactDiningOutIdentity();
  const legacyEstimate = {
    ...estimateV3Payload(uniqueId('legacy-menu-publication'), 'Integration Legacy Published Menu'),
    p_pricetrace_identity: legacyExactIdentity
  };
  const legacyImport = await callCanonicalV3(ownerA, legacyEstimate);
  await verifyCanonicalResult(ownerA, legacyImport, 'food-estimate.v1', 'food_image_estimate');
  await expectOk(
    'legacy dining-out identity attach regression',
    '/rest/v1/rpc/attach_dining_out_menu_identity_v1',
    {
      method: 'POST',
      headers: rpcHeaders(ownerA),
      body: JSON.stringify({
        p_nutrition_food_id: legacyImport.nutrition_food_id,
        p_restaurant_id: legacyExactIdentity.restaurant_id,
        p_restaurant_location_id: legacyExactIdentity.restaurant_location_id,
        p_restaurant_menu_id: legacyExactIdentity.restaurant_menu_id,
        p_catalog_product_id: legacyExactIdentity.catalog_product_id
      })
    }
  );
  const legacyPublicationBody = await expectOk(
    'legacy dining-out publication regression',
    '/rest/v1/rpc/set_dining_out_menu_publication_v1',
    {
      method: 'POST',
      headers: rpcHeaders(ownerA),
      body: JSON.stringify({ p_nutrition_food_id: legacyImport.nutrition_food_id, p_publish: true })
    }
  );
  assertEqual(legacyPublicationBody[0].visibility, 'public', 'legacy publication remains available');
  assertEqual(
    legacyPublicationBody[0].catalog_product_id,
    legacyExactIdentity.catalog_product_id,
    'legacy publication exact catalog identity'
  );
  console.log('PASS atomic OCR dining-out publication, idempotency, owner, identity, and legacy publication checks');

  const externalReference = {
    ...JSON.parse(JSON.stringify(EXTERNAL_REFERENCE_FIXTURE)),
    p_idempotency_key: uniqueId('external-reference')
  };
  const externalReferenceResult = await callCanonicalV3(ownerA, externalReference);
  await verifyExternalReference(
    ownerA,
    externalReferenceResult,
    externalReference,
    externalReference.p_food_name
  );
  await verifyOwnerIsolation(ownerA, ownerB, externalReferenceResult);
  await verifyV3ReadIsolation(ownerB, externalReference.p_food_name);

  const externalReferenceReplay = await callCanonicalV3(ownerA, externalReference);
  assertEqual(
    externalReferenceReplay.canonical_import_id,
    externalReferenceResult.canonical_import_id,
    'external replay id'
  );
  assertEqual(
    externalReferenceReplay.nutrition_food_id,
    externalReferenceResult.nutrition_food_id,
    'external replay food id'
  );
  assertEqual(externalReferenceReplay.idempotent_replay, true, 'external replay flag');
  assertEqual(
    (await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
      canonical_import_id: externalReferenceResult.canonical_import_id
    })).length,
    7,
    'external replay must not add provenance rows'
  );
  console.log('PASS external-reference.v1 idempotency replay');

  const externalSourceMismatch = {
    ...JSON.parse(JSON.stringify(externalReference)),
    p_idempotency_key: uniqueId('external-source-mismatch'),
    p_provenance: {
      ...externalReference.p_provenance,
      source_type: 'product_label_ocr'
    },
    p_nutrient_provenance: Object.fromEntries(
      Object.entries(externalReference.p_nutrient_provenance).map(([key, value]) => [
        key,
        { ...value, source_type: 'product_label_ocr' }
      ])
    )
  };
  await assertFunctionRejected(
    'external-reference source_type mismatch rejection',
    () => callCanonicalV3(ownerA, externalSourceMismatch)
  );

  const externalBlankReference = {
    ...JSON.parse(JSON.stringify(externalReference)),
    p_idempotency_key: uniqueId('external-blank-reference'),
    p_source_document_ref: ' ',
    p_provenance: {
      ...externalReference.p_provenance,
      source_reference: ' '
    }
  };
  await assertFunctionRejected(
    'external-reference blank source reference rejection',
    () => callCanonicalV3(ownerA, externalBlankReference)
  );

  const externalMissingNutrient = {
    ...JSON.parse(JSON.stringify(externalReference)),
    p_idempotency_key: uniqueId('external-missing-nutrient')
  };
  delete externalMissingNutrient.p_required_nutrients.sodium_mg;
  await assertFunctionRejected(
    'external-reference missing required nutrient rejection',
    () => callCanonicalV3(ownerA, externalMissingNutrient)
  );

  const externalPriceTraceIdentity = {
    ...JSON.parse(JSON.stringify(externalReference)),
    p_idempotency_key: uniqueId('external-pricetrace-authority'),
    p_pricetrace_identity: { catalog_product_id: crypto.randomUUID() }
  };
  await assertFunctionRejected(
    'external-reference client identity authority rejection',
    () => callCanonicalV3(ownerA, externalPriceTraceIdentity)
  );
  const invalidRestaurantHierarchy = {
    ...estimateV3Payload(uniqueId('estimate-v3-hierarchy-rejected'), 'Integration Invalid Restaurant Hierarchy'),
    p_sub_brand_name: 'Must not be applied to restaurant nutrition'
  };
  await assertFunctionRejected(
    'v3 restaurant packaged hierarchy rejection',
    () => callCanonicalV3(ownerA, invalidRestaurantHierarchy)
  );
  const aliasConflict = {
    ...packagedHierarchyLabelPayload(
      uniqueId('v3-brand-alias-conflict'),
      'Integration Conflicting Brand Alias'
    ),
    p_brand: 'Integration Legacy Alias',
    p_brand_name: 'Integration Explicit Brand'
  };
  await assertFunctionRejected(
    'v3 legacy p_brand and explicit p_brand_name conflict',
    () => callCanonicalV3(ownerA, aliasConflict)
  );
  const unverifiedV3 = packagedHierarchyLabelPayload(
    uniqueId('unverified-v3'),
    'Integration Unverified v3 Import'
  );
  unverifiedV3.p_user_verified = false;
  await assertFunctionRejected(
    'v3 unverified import rejection',
    () => callCanonicalV3(ownerA, unverifiedV3)
  );

  const label = labelPayload(uniqueId('label'), 'Integration Label Food');
  const labelResult = await callCanonical(ownerA, label);
  await verifyCanonicalResult(ownerA, labelResult, 'nutrition-label.v1', 'product_label_ocr');
  const productLabelIdentity = exactDiningOutIdentity();
  await assertFunctionRejected(
    'OCR publication rejects product-label Nutrition',
    () => callOcrDiningOutPublication(ownerA, {
      p_idempotency_key: uniqueId('ocr-publication-product-label'),
      p_canonical_import_id: labelResult.canonical_import_id,
      p_nutrition_food_id: labelResult.nutrition_food_id,
      p_restaurant_id: productLabelIdentity.restaurant_id,
      p_restaurant_location_id: productLabelIdentity.restaurant_location_id,
      p_restaurant_menu_id: productLabelIdentity.restaurant_menu_id,
      p_catalog_product_id: productLabelIdentity.catalog_product_id
    })
  );
  const rejectedProductLabel = await getRows(
    ownerA,
    'nutrition_foods',
    { id: labelResult.nutrition_food_id },
    'visibility,publication_revision'
  );
  assertEqual(rejectedProductLabel[0].visibility, 'private', 'product-label rejection preserves privacy');
  assertEqual(rejectedProductLabel[0].publication_revision, 0, 'product-label rejection creates no publication');

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

  const hierarchyReplay = await callCanonicalV3(ownerA, hierarchy);
  assertEqual(hierarchyReplay.canonical_import_id, hierarchyResult.canonical_import_id, 'v3 replay id');
  assertEqual(hierarchyReplay.nutrition_food_id, hierarchyResult.nutrition_food_id, 'v3 replay food id');
  assertEqual(hierarchyReplay.idempotent_replay, true, 'v3 replay flag');
  assertEqual(hierarchyReplay.sub_brand_name, hierarchy.p_sub_brand_name, 'v3 replay sub-brand');
  assertEqual((await getRows(ownerA, 'nutrition_food_nutrient_provenance', {
    canonical_import_id: hierarchyResult.canonical_import_id
  })).length, 7, 'v3 replay must not add provenance rows');
  console.log('PASS v3 idempotency replay');

  const hierarchyCollision = {
    ...hierarchy,
    p_sub_brand_name: 'Integration Changed Sub Brand'
  };
  await assertFunctionRejected(
    'v3 changed hierarchy with same key rejection',
    () => callCanonicalV3(ownerA, hierarchyCollision)
  );

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
  await assertFunctionRejected('anonymous canonical v3 RPC rejection', async () => {
    await expectOk(
      'anonymous canonical v3 RPC',
      '/rest/v1/rpc/import_canonical_nutrition_v3',
      {
        method: 'POST',
        headers: authHeaders(null),
        body: JSON.stringify(packagedHierarchyLabelPayload(
          uniqueId('anonymous-v3'),
          'Anonymous v3 Import'
        ))
      }
    );
  });
  await assertFunctionRejected('anonymous external-reference v3 RPC rejection', async () => {
    await expectOk(
      'anonymous external-reference v3 RPC',
      '/rest/v1/rpc/import_canonical_nutrition_v3',
      {
        method: 'POST',
        headers: authHeaders(null),
        body: JSON.stringify({
          ...JSON.parse(JSON.stringify(EXTERNAL_REFERENCE_FIXTURE)),
          p_idempotency_key: uniqueId('anonymous-external-reference')
        })
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
