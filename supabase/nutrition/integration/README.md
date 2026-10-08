# Nutrition canonical import integration test

`canonical-import.integration.mjs` exercises the real Nutrition Supabase project
through Auth, PostgREST, and the canonical import RPCs. It verifies owner-scoped
RLS, anonymous rejection, the v1/v2 evidence contracts, the authoritative v3
endpoint with the existing `nutrition-label.v1` and `food-estimate.v1` input
semantics plus the `external-reference.v1` public-source contract, all four
explicit packaged-product hierarchy fields, seven
provenance rows, replay/collision behavior including changed hierarchy,
v1/v2 idempotency namespace separation, exact product↔Nutrition links,
restaurant hierarchy rejection and null read round-trip, strict `p_brand` /
`p_brand_name` alias handling, malformed payload rejection, and the direct
`nutrition_foods` write boundary. It also verifies the OCR dining-out
publication RPC: private canonical import, all four required PriceTrace IDs,
owner/product-label rejection, stored identity conflict rejection, atomic
success, approved link, publication event, same-key replay, changed-payload
conflict, and the existing manual publication RPC. It also verifies the
external source with the OCR-App
`yeonsik-ocr.v2.packaged-product.text-lookup.example.json` fixture, including
the public URL, `external-nutrition-lookup.v1`, unchanged basis amount/unit,
observed seven-nutrient provenance, cross-owner isolation, and idempotent replay.

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

The suite uses real test-owner rows and therefore remains opt-in. It removes
the rows created by the suite after completion.

For an injected failure at publication-event insertion, run
`ocr-dining-out-publication-rollback.sql` against a disposable/local Nutrition
database after all migrations. The SQL script opens a transaction and rolls it
back after proving that identity, link, visibility, publication-event, and
idempotency writes do not survive the failure.

The URL and anon key can be supplied as `NUTRITION_DB_URL` and
`NUTRITION_DB_ANON`, or by the corresponding `NUTRITION_SUPABASE_*` aliases.
When owner credentials are omitted, the service-role key is used to create two
temporary users and remove them after the run.

## Disposable migration history replay

The historical-replay/replay.mjs runner starts two uniquely named local Supabase projects with Docker and no linked project reference:

- a historical replay that runs all 28 recovered migrations from an empty database;
- a second fresh replay that runs those same 28 migrations, applies all three pending migrations, verifies the final schema and grants, runs the rollback fixture, and runs the authenticated `external-reference-only` integration mode.

The runner first applies the real SQL through 20260814065526_product_nutrition_link_pricetrace_metadata.sql. It then loads historical-replay/pre_20260814065823_kaguri.sql, a disposable-only synthetic prerequisite, and applies unchanged historical SQL from 20260814065823 onward. The runner derives the one selector UUID from that original migration; the fixture contains no production owner or Nutrition row data and is not part of normal seed configuration.

The final replay snapshots public table columns, constraints, and function definitions before 20261002120000_external_reference_nutrition_import_v1.sql. Assertions require that migration to add only its external-reference helper, preserve Sep20 constraints and the legacy RPC body, and update only the canonical v3 dispatcher. The current migration has no dynamic function patch. The runner fails closed if one is introduced before exact-anchor validation is added.

The disposable replay's integration mode verifies the new external-reference route through the v3 dispatcher: authenticated owner isolation, private visibility, exactly seven nutrient provenance rows with the same public source URL, same-payload idempotent replay, changed-payload conflict, malformed provenance rejection, and anonymous rejection. It deliberately does not call the Sep20 generic v3 legacy branch. On a vanilla local Postgres replay that existing branch currently errors in the already-applied `20260920091249_nutrition_verified_import.sql` at its unqualified `nutrition_food_id` lookup; that remote-applied SQL remains unchanged and the generic legacy path is not proven by this replay.

Run with Node.js, the Supabase CLI, Docker, and the integration package dependencies installed:

    npm ci --prefix supabase/nutrition/integration
    node supabase/nutrition/integration/historical-replay/replay.mjs

The runner removes only its uniquely created temporary project directories and Supabase data volumes. It never links to a hosted project and never runs db push.

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
