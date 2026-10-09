# OCR V5 origin through existing Fitness contracts

Production source change unnecessary. The latest inspected main is `73b18b2`.
There is no V5 parser, Nutrition table, Meal schema or V5 RPC in this change.

## Existing receiver boundaries

- `import_canonical_nutrition_v2` and `import_canonical_nutrition_v3` accept
  Fitness-owned scalar fields and `food-estimate.v1`, not the outer OCR envelope.
  `p_provenance.schema_version=yeonsik-ocr.v5` is retained as audit metadata;
  neither generic estimate route applies an OCR version whitelist.
- A restaurant food-photo estimate retains all seven required nutrients,
  matching nutrient evidence/value status, at least one estimate, numeric
  confidence in `[0,1]`, and `p_user_verified=true`. V3 restaurant hierarchy
  fields remain null. Nutrition import is private and does not create a Meal.
- `publish_verified_ocr_dining_out_nutrition_v1` is the separate explicit
  publication boundary. It requires the owner canonical import ID, Nutrition
  food ID, and four exact PT restaurant/location/menu/catalog UUIDs. It creates
  the approved link, public visibility and event atomically, preserving replay
  and conflict checks. Missing identity fails closed and leaves the food private.
- Fitness cannot query the separate PT database or authenticate a UUID as an
  exact PT match. OCR must use server-issued PT response/checkpoint metadata,
  require exact authority, and leave ambiguous/unresolved linkage pending.
  Never invent IDs or pass candidate IDs to make this publication succeed.
- `import_verified_meal_v1` accepts explicit `p_eaten_at`, exact Nutrition IDs,
  consumed amount/unit and source provenance. `p_source.schema_version` and item
  provenance may contain the V5 origin; there is no OCR version whitelist.
  Its offset timestamp and immutable nutrient snapshot rules remain unchanged.

## OCR projection requirements

Send only the existing Fitness payload. Preserve origin and photo evidence in
provenance; do not copy purchase price/date/payment facts into Nutrition values.
Generic provenance is extensible JSON, so this separation is the OCR projection's
responsibility, not a new Fitness envelope validation rule.

Map the artifact key to the returned Fitness `nutrition_food_id` and
`canonical_import_id`. Map exact PT `authoritativeIds.restaurantId`,
`restaurantLocationId`, `restaurantMenuId`, `catalogProductId` to the existing
publication parameters. Keep these IDs in downstream metadata, not OCR source
facts. Unresolved/needs-review inputs remain private/pending/review-required.

Call Meal only for explicit consumption. Map the actual `consumed_at` to
`p_eaten_at`; purchase, order and payment timestamps must never be substitutes.
No consumption means no Meal RPC. Meal remains independent of publication.
Fitness cannot detect a purchase timestamp incorrectly labelled by the caller as
consumption; the sender must preserve this authority boundary.

Use stable owner/item/revision idempotency keys independently for Nutrition,
publication and Meal. A changed request with an existing key remains a conflict.

## Executable regression and known baseline defect

`supabase/nutrition/integration/v5-origin.integration.mjs` uses a caller-supplied,
externally installed PGlite PostgreSQL engine; no dependency or lockfile changes.
It applies all 31 unchanged migrations with the existing synthetic historical
Kaguri prerequisite and runs rollback-only synthetic fixtures. SQL permissions,
RLS and RPC execution are real; Auth helpers are local bootstrap stubs. HTTP JWT,
hosted Supabase and device behavior are not covered.

The new `ocr-v5-origin-compatibility.sql` covers V5-origin V2/V3 estimate import,
seven provenance rows, private-first and no inferred Meal, exact publication,
missing/ambiguous authority blocking, publication replay/conflict, V3 Nutrition
replay, explicit Meal, purchase-time-only rejection, Meal replay/conflict,
legacy label paths, missing nutrient/confidence, foreign owner and missing auth.
The existing publication rollback fixture is also executed unchanged.

**Existing V2 defect:** estimate replay fails with PostgreSQL `42702` because
`import_canonical_nutrition_v2` selects an unqualified `estimation_evidence_id`
from `nutrition_verified_imports`, conflicting with its output parameter.
The test reproduces this baseline failure explicitly; it is not a green V2
runtime replay result. Latest main's `20261002130000` forward fix qualifies the
V3 legacy replay lookup, but does not target V2. The current OCR `NutritionSupabaseGateway.importCanonicalNutritionV3` already targets this existing V3 endpoint. V5 should use the existing V3
route. V2 production was deliberately not modified under this task's scope.

Commands and results on 2026-10-09:

- `gradlew.bat testDebugUnitTest assembleDebug --console=plain`: PASS; 502 tests / 106 suites, zero failures.
- `node supabase/nutrition/integration/v5-origin.integration.mjs <pglite-directory>`:
  V5 V3 compatibility and unchanged publication rollback PASS; known V2 replay
  failure reproduced. Do not describe the full V2/V3 runtime regression as green.
- Remote migration/RPC/Auth, OCR-to-Fitness and Android device: UNVERIFIED.

No migrations or production files were added or changed. No remote writes,
commit, push or deployment are part of this investigation.
