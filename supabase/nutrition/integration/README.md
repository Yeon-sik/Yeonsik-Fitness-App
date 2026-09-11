# Nutrition canonical import integration test

`canonical-import.integration.mjs` exercises the real Nutrition Supabase project
through Auth, PostgREST, and the canonical import RPCs. It verifies owner-scoped
RLS, anonymous rejection, the v1/v2 evidence contracts, the authoritative v3
endpoint with the existing `nutrition-label.v1` and `food-estimate.v1` input
semantics, all four explicit packaged-product hierarchy fields, seven
provenance rows, replay/collision behavior including changed hierarchy,
v1/v2 idempotency namespace separation, exact product↔Nutrition links,
restaurant hierarchy rejection and null read round-trip, strict `p_brand` /
`p_brand_name` alias handling, malformed payload rejection, and the direct
`nutrition_foods` write boundary.

The v3 endpoint does not accept `nutrition-label.v3`, `food-estimate.v3`, or a
`p_category_hierarchy` array. Packaged-product callers send
`p_manufacturer_name`, `p_brand_name`, `p_sub_brand_name`, and `p_product_name`
as explicit nullable scalar parameters. Restaurant estimate callers send those
four fields as `null`; no packaged hierarchy is inferred from restaurant data.
See [`docs/nutrition-canonical-provenance.v3.md`](../../../docs/nutrition-canonical-provenance.v3.md)
for the exact named-parameter and response contract.

The test is intentionally opt-in because it creates real rows. Use a dedicated
integration project or dedicated test users. It requires a service-role key for
cleanup; the key is read from the environment and never printed.

```powershell
cd supabase/nutrition/integration
npm install
$env:NUTRITION_INTEGRATION_ALLOW_REMOTE = "true"
$env:NUTRITION_INTEGRATION_SERVICE_ROLE_KEY = "<nutrition service role key>"
$env:NUTRITION_INTEGRATION_EMAIL_A = "<dedicated test user A>"
$env:NUTRITION_INTEGRATION_PASSWORD_A = "<dedicated test password A>"
$env:NUTRITION_INTEGRATION_EMAIL_B = "<dedicated test user B>"
$env:NUTRITION_INTEGRATION_PASSWORD_B = "<dedicated test password B>"
npm test
```

The URL and anon key can be supplied as `NUTRITION_DB_URL` and
`NUTRITION_DB_ANON`, or by the corresponding `NUTRITION_SUPABASE_*` aliases.
When owner credentials are omitted, the service-role key is used to create two
temporary users and remove them after the run.

## Verified Meal ingest

`meal-import.integration.mjs` exercises the receiver path for a v2
`FITNESS_MEAL` projection: it imports a verified `meal_component_estimate` as
an exact private `nutrition_foods` row, adds a test micronutrient, calls
`import_verified_meal_v1`, and verifies the Meal parent, consumed amount/unit,
Nutrition snapshots, micronutrient snapshot, nullable `restaurant_menu_id`,
idempotent replay/conflict, owner isolation, and direct-write rejection.

It uses the same environment variables and cleanup rules as the canonical
Nutrition harness, but has its own opt-in flag:

```powershell
$env:NUTRITION_MEAL_INTEGRATION_ALLOW_REMOTE = "true"
npm run test:meal
```

Run it only against a dedicated integration project or dedicated test users.
The OCR App's sender must still resolve each `nutrition_client_key` to the
returned exact `nutrition_food_id` before sending the Meal RPC; this receiver
harness does not infer IDs from names.
