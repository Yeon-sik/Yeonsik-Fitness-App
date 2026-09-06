# Fitness verified Meal ingest v1

Fitness owns the detailed `meal_records`, `meal_record_items`, and
`meal_record_item_nutrients` rows. Nutrition remains the owner of the referenced
`nutrition_foods` identity. The OCR App may submit a verified
`yeonsik-ocr.v2` `FITNESS_MEAL` projection only through the authenticated
`import_verified_meal_v1` RPC in the Fitness-owned Nutrition Supabase project.

The existing `import_verified_nutrition_v1` and
`import_canonical_nutrition_v2` RPCs remain Nutrition-only imports. They do not
create Meal rows. A Meal projection first resolves every nutrition artifact to an
exact `nutrition_food_id`, then submits the consumed amount and unit below.

## Meal RPC

```text
POST {NUTRITION_SUPABASE_URL}/rest/v1/rpc/import_verified_meal_v1
Authorization: Bearer <user access token>
apikey: <nutrition anon key>
Content-Type: application/json
```

The input contract is:

```json
{
  "p_idempotency_key": "ocr:meal-01J9:revision-2",
  "p_eaten_at": "2026-09-05T19:20:00+09:00",
  "p_items": [
    {
      "client_key": "component-free-side-1",
      "nutrition_food_id": "fitness-owned-nutrition-food-id",
      "amount": 50,
      "unit": "g",
      "confidence": 0.91,
      "source_provenance": {
        "schema_version": "yeonsik-ocr.v2",
        "source_document_ref": "ocr-app://ingestion/01J9/document/1",
        "artifact_key": "nutrition:component-free-side-1",
        "verified_consumption": true
      }
    }
  ],
  "p_source": {
    "schema_version": "yeonsik-ocr.v2",
    "projection": "FITNESS_MEAL",
    "source_app": "ocr-app",
    "meal_kind": "dining_out",
    "menu": "불고기 정식",
    "source_document_ref": "ocr-app://ingestion/01J9/document/1"
  },
  "p_pricetrace_identity": null
}
```

Required boundaries:

- `p_eaten_at` must be an ISO offset timestamp. Its supplied offset's local date
  is the Meal record date; naive/local timestamps are rejected. Future dates are
  rejected using the same rule as `MealEntryPolicy.requireRecordDate`.
- Each item needs an exact Nutrition food ID and a positive numeric consumed
  amount/unit. Supported mass and volume units are converted to the Nutrition
  basis unit. Count units are accepted only when they match the basis count unit.
- `amount`/`unit` are retained as the actual input. The normalized quantity and
  every currently known Nutrition value are copied to the Meal item snapshot.
  Later edits, replacement, or deletion of the Nutrition row cannot change the
  stored Meal values. Unknown values remain `NULL`, never zero.
- `source_provenance` is stored on each item and `p_source` is stored on the
  parent Meal. The optional PriceTrace object stores exact IDs only; URLs and a
  non-PriceTrace namespace are rejected. No name matching is performed.
- The same authenticated owner and idempotency key replay only an identical full
  payload. A changed payload with the same key is rejected.

The response contains `meal_import_id`, `meal_record_id`,
`idempotent_replay`, the offset `eaten_at`, the policy-derived `record_date`,
the item count, exact Nutrition food IDs, and
`fitness-meal-verified-import.v1`.

## `meal_component_estimate` NutritionFood

An OCR food-photo/menu estimate that is not an existing restaurant menu identity
uses the separate authenticated `import_meal_component_estimate_v1` RPC. It
creates a private `nutrition_foods` row with `source_type=meal_component_estimate`
and stores per-nutrient evidence in
`nutrition_meal_component_nutrient_provenance`. All seven required Nutrition
values and their evidence references are required, at least one value must be
explicitly estimated, and numeric confidence evidence is required.

The component input may contain `restaurant_menu_id: null`; the RPC preserves
that absence and never invents a restaurant menu ID. The resulting
`nutrition_food_id` is then used in `p_items` for the Meal RPC. A free side dish
therefore follows the same exact-ID and snapshot path as any other Meal item.

## Ownership and compatibility

Meal tables have authenticated owner-scoped read policies and no direct
authenticated insert/update/delete grants. The two RPCs are the write boundary.
Nutrition catalog sync continues to use only `nutrition_foods`,
`nutrition_food_nutrients`, `nutrition_food_components`, and
`product_nutrition_links`; detailed Meal rows are not pulled into the catalog
repository or shared Personal OS summary sync.

The existing manual Android Meal path and the two Nutrition import RPCs are not
replaced. They retain their current local/remote contracts. The new migration
adds the detailed remote ingest path beside them.

## Verification boundary

`VerifiedMealImportContractTest` and `MealEntryPolicyTest` verify the repository
contract and date semantics locally. The opt-in
`supabase/nutrition/integration/meal-import.integration.mjs` test exercises
authenticated Auth/PostgREST/RPC calls, component-estimate creation, Meal row
creation, snapshot scaling, nullable restaurant-menu identity, replay/conflict,
owner isolation, and direct-write rejection against a real Nutrition project.
It requires the migration to be applied and is not run automatically because it
creates real rows. A successful local Gradle build is not remote Supabase or
OCR-device proof.
