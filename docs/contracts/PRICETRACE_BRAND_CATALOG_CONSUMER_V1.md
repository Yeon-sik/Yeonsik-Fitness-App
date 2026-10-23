# Fitness → PriceTrace Brand Catalog consumer v1

Fitness owns NutritionFood, nutrition values, local label/manufacturer facts and product–nutrition links. PriceTrace owns canonical Brand UUIDs, canonical names, aliases, and the direct-parent hierarchy. This consumer stores an optional, explicitly selected external Brand reference on a NutritionFood. It does not introduce a Fitness Brand master.

## Baseline and delivery boundary

- PriceTrace main: `08b8b0273f3457eba4ad878f26a74e306b332411`.
- Fitness starting main: `d0f93a10371061e5fc06b7c948c39a55810120f9`.
- Fitness feature branch: `feat/pricetrace-brand-catalog`; review before merge.
- Room: 56 → 57. Legacy SQLite 50 and Room handoff 51 remain historical contracts.
- PriceTrace and OCR pending Brand-resolution branches are outside this delivery.

## Selection and snapshots

`PriceTraceBrandSelection(brandId, canonicalName, parentBrandId?, parentCanonicalName?)` validates complete UUID text, a nonblank canonical name, and a parent name only when a parent ID exists. A nonnull parent ID with a null parent name is valid and means an unknown parent label, not a root Brand.

| Nutrition field | Type | Meaning |
| --- | --- | --- |
| `pricetrace_brand_id` | UUID remote / nullable TEXT local | Selected most-specific canonical Brand identity |
| `pricetrace_brand_name_snapshot` | nullable text | Canonical name at explicit selection time |
| `pricetrace_parent_brand_id_snapshot` | UUID remote / nullable TEXT local | Direct parent at selection time |
| `pricetrace_parent_brand_name_snapshot` | nullable text | Optional direct-parent label at selection time |

All four are absent/null on legacy rows. Selection, change and clear update the tuple atomically. Only `pricetrace_brand_id` is identity; the other three are display snapshots. No cross-database foreign key, automatic backfill, inferred parent, ancestor flattening, silent rename or reparent refresh is performed.

The existing `brand`, `manufacturer_name`, `brand_name`, `sub_brand_name`, `product_name`, package metadata and source facts remain independent. Product grouping still prefers the existing approved `standard_product_id`, then `catalog_product_id`, then the unchanged local hierarchy. Neither `CanonicalPackagedProductKey` nor `ProductNutritionLink` uses the Brand UUID.

## Local persistence and owner boundary

`NutritionFood` and `NutritionFoodInput` expose an optional typed selection. Existing builders and Java constructors continue to work without it. Room 57 adds only the four nullable TEXT columns. `V56_TO_V57`, the complete legacy migration chain, the pre-Room fresh schema and generated `57.json` all describe the same resulting table. Applied schema exports and migrations are preserved.

`NutritionCatalogRepository.setPriceTraceBrand(foodId, ownerId, selection?)` is the sole focused local mutation. Within a transaction it verifies the current Nutrition owner, an active owned private row and the existing packaged-food classification. It updates all four values, `updated_at`, and `revision + 1`; null means explicit clear. Account changes and the focused write share the repository owner lock. Public rows, other owners, ingredients, recipes and dining-out menus cannot use this mutation.

General local upserts retain a previous selection when no Brand mutation was requested. Domain reads reconstruct the typed tuple. Normal sync exports and applies all four fields with the existing food revision/timestamp conflict policy. A newer remote all-null tuple clears the local link; an older remote row cannot revert a newer local revision. Legacy payloads without Brand columns parse as null. Partial or non-text Brand snapshots are rejected.

Backup format remains `fitness-os.local-backup` version 1, independent of Room 57. Export includes the nullable columns through the existing schema-based codec. Older backups may omit them. Backup validation checks the entire tuple before restore can change existing data.

## Public PriceTrace read contract

```text
public.get_public_brand_catalog_v1(
    p_query text default null,
    p_limit integer default 50,
    p_offset integer default 0
)
POST /rest/v1/rpc/get_public_brand_catalog_v1
{ "p_query": text|null, "p_limit": integer, "p_offset": integer }
```

`BrandCatalogV1Client` requires exactly these eight response keys on every row:

```text
brand_id uuid
canonical_name text
parent_brand_id uuid nullable
parent_canonical_name text nullable
matched_name text
match_kind "canonical" | "alias"
logo_url text nullable
official_site_url text nullable
```

The client rejects unknown/missing fields, non-text values, malformed UUIDs, blank names and inconsistent parent tuples. It preserves server order and the canonical ID even when an alias matched. There is no fuzzy matching, local score, automatic selection or duplicate Brand authority.

Requests use the existing `NutritionIntegrationService.priceTraceConfig` and `SupabaseConfig`. An `apikey` header carries an existing public publishable or legacy anon key. User access/refresh tokens are not used; there is no sign-in requirement. Secret keys, service-role keys, authenticated-user JWTs and unrecognized key formats are rejected. HTTPS, timeouts, response-size limits and redirect rejection are enforced. Errors remain errors rather than becoming successful empty pages.

Each page is 25 rows in the UI; the transport allows only 20–50 and nonnegative offsets. “더 보기” requests the next offset and retains server order. Search uses a 300 ms debounce, two bounded worker threads, cancellation and a request generation plus owner/config scope. Changing the query, account, configuration or editor target invalidates old results. Configuration listeners are released with the Meal ViewModel.

## Fitness Supabase migration and read contract

Migration: `20261023121311_pricetrace_brand_catalog_consumer.sql`.

It adds the four nullable columns, a complete-snapshot check, and the same four values to both sides of the existing `bump_nutrition_food_revision()` content tuple. All existing comparisons and revision arithmetic remain unchanged. Existing RLS policies and grants are preserved.

New RPC:

```sql
public.get_nutrition_read_v4(p_query text default null)
```

It returns the current v3 fields in their existing order:

```text
contract_version text, nutrition_food_id text, brand text, name text, kind text,
basis_amount numeric, basis_unit text, prep_state text,
nutrition_values jsonb, micronutrients jsonb,
source_type text, source_reference text, source_revision text, revision integer,
catalog_product_id uuid, standard_product_id uuid,
manufacturer_name text, brand_name text, sub_brand_name text, product_name text
```

followed by exactly the four Brand fields listed above. The function is `STABLE SECURITY INVOKER`, uses an empty `search_path`, revokes default PUBLIC execution and grants execution to `anon` and `authenticated`. It wraps the current v3 projection under the same row visibility and ordering. In particular, it inherits v3's `contract_version` value so the required literal equality holds:

```text
v4 row minus the four Brand keys == current v3 row
```

The v4 RPC name identifies the added wire shape. No existing v1/v2/v3 definition or return field is edited. No import v4, publication rewrite or product-link change is introduced. Normal Fitness catalog sync continues to use its existing row transport.

## UX and offline behavior

The primary FOOD registration flow keeps the free-text label Brand input and adds an optional “PriceTrace 브랜드 연결” section only for PRODUCT. Search starts with that input but never selects a result automatically. Selecting stores the canonical UUID/name/direct parent separately. Material edits to the unsaved local Brand text or switching away from PRODUCT invalidate the draft selection. The selected draft survives saved-state restoration for the same Nutrition owner.

The existing-food library exposes focused link/change/clear actions only for editable private products; public rows display stored metadata. Existing-food search seeds from `subBrandName`, then `brandName`, then `brand`. The repository repeats edit authorization at write time. Leaving a focused editor clears its target so a later draft action cannot mutate the previously edited product.

Loading, empty, failure and retry states are explicit. Offline or missing PriceTrace configuration does not prevent local registration, meal recording, local search, or explicit unlink. Stored snapshots remain visible if search fails or the Brand disappears. Empty public search shows that the stored Brand is currently unconfirmed while keeping the ID until explicit change/clear. Search never silently updates previously stored names or parents.

## Repeatable validation

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
git diff --check

cd supabase/nutrition/integration
npm.cmd ci --ignore-scripts
npm.cmd run test:brand
```

The SQL harness pins a test-only PGlite runtime and replays the 35 baseline migrations plus this migration. It uses the existing synthetic prerequisite for the historical Kaguri repair. Git LF SQL is replayed independent of Windows checkout line endings. Tests compare real PostgreSQL function definitions and full v3/v4 projections, exercise all four revision changes, null/parent variants, malformed tuples, atomic clear and anonymous/owner RLS. There are no remote writes in this test.

JVM strict JSON tests use a pinned test-only `org.json` implementation rather than Android's stub. The optional `BrandCatalogLiveSmokeTest` uses the production client and parser with `PRICETRACE_LIVE_SMOKE=1`, `PRICETRACE_SMOKE_URL` and `PRICETRACE_SMOKE_ANON_KEY`, without a user session. Runtime keys are never committed.

Android tests use isolated Room fixtures and a dedicated review emulator. Coverage includes real Room 56→57 preservation, generated/fresh schema parity, backup old/new/invalid documents, owner/public write rejection, draft restoration, explicit existing change/clear, alias/parent rendering, offline snapshots, stale noncancellable responses and editor-target isolation. Existing Nutrition, hierarchy, product-link, meal/backup and historical Room regressions remain part of validation.

Regression fixture maintenance keeps the assertions and production validation intact: simulated v14/v15 backups omit the already-existing v55/v56 settings tables, verified-search fixtures explicitly seed fresh Room databases, and migration fixtures accept an omitted empty `indices` array in Room's exported JSON. The new search cancellation path does not publish an empty state before owner/draft restoration, so previous composition and food drafts remain recoverable.

## Remote deployment boundary

Use existing legitimate Fitness Nutrition access only. Inspect actual migration history, function definitions and advisors first. This environment has historical differences between the repository and linked migration ledger, so remote history is fetched into an isolated staging directory; the repository's history is not repaired, rewritten or pushed wholesale. A dry-run must list only this migration before deployment.

Post-deploy validation compares v1/v2/v3 hashes, checks all four columns and v4 privileges/RLS, and exercises a synthetic private Nutrition fixture inside a transaction that rolls back. Simulated roles prove database policy behavior; they are not a claim of a real user's authenticated HTTP session. PriceTrace verification remains anonymous/read-only. Real user nutrition rows are never relabeled or backfilled.

## Validation evidence — 2026-10-23

- App JVM suite: 576 tests, 575 passed, one existing ignored test; production-client anonymous Brand smoke passed with 25 returned rows and an exact canonical-name query.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:lintDebug` and `git diff --check`: passed. No production dependency was added.
- Dedicated Android 36.1 / 16 KB emulator: 39 related instrumentation tests passed; the two Compose UI tests passed again after the final title/contrast adjustment. Screens covered registration, existing private/read-only metadata, aliases and unknown parent labels.
- Actual Room v56 fixture → v57: food, nutrients, meal rows/items, product links and workout rows/sets retained their original columns. The resulting nutrition table matches generated Room creation and the production pre-Room fresh schema.
- New, legacy-without-Brand and rejected partial backup documents passed round-trip/state-preservation checks. Existing composition and optional-nutrient drafts also restore successfully.
- SQL: all 35 baseline migrations plus the new migration replayed successfully in PostgreSQL/PGlite. Shared SQL assertions also belong to the native disposable Supabase replay, and the Brand SQL gate is wired into its PR workflow.
- The Fitness Nutrition migration was deployed using existing CLI authorization after a one-migration dry-run. Remote ledger contains `20261023121311` exactly once; all four columns have the expected nullable UUID/text types.
- Remote `get_nutrition_read_v4(text)` is invoker, callable by anon/authenticated. Anonymous HTTP returned 15 public rows; deleting the four added keys yields the exact v3 response.
- Remote synthetic private fixture: set/change/clear, revision advancement, v4 common projection and cross-owner/anonymous RLS checks passed inside a transaction that rolled back. Retained Brand-linked Nutrition rows after the smoke: 0. No real-user row was edited.
- Nutrition RLS remained enabled and the policy hash stayed `10997508929411f33299c4a4509091e4`. Advisors reported no new findings; 18 pre-existing warnings remain outside this feature.

The deployed legacy RPC definition hashes were identical before and after deployment:

| RPC | MD5 of `pg_get_functiondef` before = after |
| --- | --- |
| `get_nutrition_read_v1(text)` | `57d4162ef1047ff524e727b1be92ed77` |
| `get_nutrition_read_v2(text)` | `9e87529880f6a62c4bedb2ca9669ae4a` |
| `get_nutrition_read_v3(text)` | `d54d97684f42b0b2a806f65b1745c4d9` |

Unverified boundaries: a physical Android device, a real-user authenticated HTTP sync session, and local full Docker/Supabase replay. The physical device and existing AVD data were preserved; a separate AVD was used for tests. Docker Desktop was unavailable locally, so the SQL evidence is PostgreSQL/PGlite plus the deployed database checks, rather than a claim of a local Docker run. Remote migration application and its ledger were independently confirmed despite the CLI's optional local catalog-cache warning.
