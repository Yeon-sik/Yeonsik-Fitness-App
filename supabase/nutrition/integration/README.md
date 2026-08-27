# Nutrition canonical import integration test

`canonical-import.integration.mjs` exercises the real Nutrition Supabase project
through Auth, PostgREST, and the `import_canonical_nutrition_v2` RPC. It verifies
owner-scoped RLS, anonymous rejection, the label and estimate contracts, seven
provenance rows, replay/collision behavior, v1/v2 idempotency namespace separation,
malformed payload rejection, and the direct `nutrition_foods` write boundary.

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
