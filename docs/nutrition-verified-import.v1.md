# Yeonsik verified Nutrition import contract v1

Fitness App owns the final Nutrition catalog. OCR App may call the authenticated
Nutrition Supabase Data API RPC below after the original document/photo has been
reviewed and the user has approved the values.

```text
POST {NUTRITION_SUPABASE_URL}/rest/v1/rpc/import_verified_nutrition_v1
Authorization: Bearer <user access token>
apikey: <nutrition anon key>
Content-Type: application/json
```

The RPC is deliberately final-import-only: `p_user_verified` must be `true`.
Unverified drafts stay in OCR App/local ingestion state and are not written to
`nutrition_foods` by this endpoint. Every imported row remains `visibility=
'private'`; publication is a separate existing owner action.

## Common payload

```json
{
  "p_idempotency_key": "ocr:receipt-20260827-00031:revision-2",
  "p_source_document_ref": "ocr-app://ingestion/01J8.../document/2",
  "p_evidence_type": "product_label",
  "p_food_name": "닭가슴살 오리지널",
  "p_brand": "예시푸드",
  "p_category": "processed",
  "p_basis_amount": 100,
  "p_basis_unit": "g",
  "p_required_nutrients": {
    "calories_kcal": 110,
    "carbs_grams": 2,
    "protein_grams": 23,
    "fat_grams": 1,
    "sugars_grams": 1,
    "saturated_fat_grams": 0.3,
    "sodium_mg": 420
  },
  "p_optional_nutrients": {
    "fiber_grams": 0,
    "cholesterol_mg": 45
  },
  "p_provenance": {
    "source_type": "product_label",
    "source_document_ref": "ocr-app://ingestion/01J8.../document/2",
    "source_version": "label-photo-revision-2",
    "estimated": false,
    "ocr_engine": "ocr-app",
    "reviewed_in": "ocr-app",
    "user_verified_at": "2026-08-27T02:00:00+09:00"
  },
  "p_user_verified": true,
  "p_pricetrace_identity": {
    "namespace": "pricetrace",
    "catalog_product_id": "11111111-1111-4111-8111-111111111111"
  }
}
```

`basis_amount` is the Nutrition basis, not the package total. A 300 g package
whose label is per 100 g therefore sends `100` and `g`; package total weight may
remain in `p_provenance.package_total_amount=300` and
`p_provenance.package_total_unit="g"`. Values that are not visible on the label
must be omitted from the draft and cannot be replaced with an estimate.

The seven required keys are always mandatory. Missing one is a contract error;
do not send zero as a substitute for an unreadable value. Optional values are
preserved in the import audit, and the current typed optional columns are
`fiber_grams`, `added_sugars_grams`, `trans_fat_grams`, and `cholesterol_mg`.

## Restaurant estimate subtype

Use the same RPC with `p_evidence_type=restaurant_estimate` and
set `p_provenance.source_type=manual_estimate`. The row is always stored as
`kind=external_menu`, with `basis_unit=serving` (or a clearly evidenced serving)
and `estimated=true`.

```json
{
  "p_idempotency_key": "ocr:menu-photo-01J9:revision-1",
  "p_source_document_ref": "ocr-app://ingestion/01J9.../document/1",
  "p_evidence_type": "restaurant_estimate",
  "p_food_name": "불고기 덮밥",
  "p_brand": "예시식당",
  "p_category": "recipe",
  "p_basis_amount": 1,
  "p_basis_unit": "serving",
  "p_required_nutrients": {
    "calories_kcal": 680,
    "carbs_grams": 86,
    "protein_grams": 29,
    "fat_grams": 22,
    "sugars_grams": 12,
    "saturated_fat_grams": 6,
    "sodium_mg": 1180
  },
  "p_provenance": {
    "source_type": "manual_estimate",
    "source_document_ref": "ocr-app://ingestion/01J9.../document/1",
    "estimated": true,
    "estimation_method": "menu-photo-and-description",
    "user_verified_at": "2026-08-27T02:05:00+09:00"
  },
  "p_estimation_evidence": {
    "confidence": 0.62,
    "range": {
      "calories_kcal": {"min": 540, "point": 680, "max": 850},
      "carbs_grams": {"min": 70, "point": 86, "max": 105},
      "protein_grams": {"min": 22, "point": 29, "max": 38},
      "fat_grams": {"min": 15, "point": 22, "max": 32},
      "sugars_grams": {"min": 7, "point": 12, "max": 20},
      "saturated_fat_grams": {"min": 3, "point": 6, "max": 10},
      "sodium_mg": {"min": 850, "point": 1180, "max": 1650}
    },
    "assumptions": ["밥 1공기", "불고기 1인분"]
  },
  "p_user_verified": true,
  "p_pricetrace_identity": {
    "schema_version": "dining-out-identity.v1",
    "namespace": "pricetrace",
    "restaurant_id": "22222222-2222-4222-8222-222222222222",
    "restaurant_location_id": "33333333-3333-4333-8333-333333333333",
    "restaurant_menu_id": "44444444-4444-4444-8444-444444444444",
    "catalog_product_id": "55555555-5555-4555-8555-555555555555"
  }
}
```

The response includes `nutrition_food_id`, `catalog_product_id` when present,
`estimation_evidence_id`, `visibility`, and `idempotent_replay`. The final point
values are in `nutrition_foods`; restaurant confidence/range/assumptions are in
`nutrition_estimation_evidence` and referenced from `source_reference`.

PriceTrace identity is optional. If supplied, product labels may contain only a
resolved exact `catalog_product_id`; restaurant estimates must contain all four
IDs above. OCR App/GPT must not invent or name-match these IDs. The existing
PriceTrace resolution/publication flow remains authoritative. A second active
approved link with a different `catalog_product_id` is rejected.

Retrying the same `(authenticated owner, p_idempotency_key)` with the identical
canonical payload returns the original import with `idempotent_replay=true`.
Reusing the key with any changed field is rejected. A different ingestion can
update the same catalog food when its exact PriceTrace identity or normalized
catalog key is the same; its new restaurant evidence is retained separately.

## Evidence boundary

This contract is repository-verified on the `feat/nutrition-verified-import`
working tree based on `origin/main` commit
`a468939714f3a8cf854b62081f1b820f7620a125` (2026-08-27). The local command
`.\gradlew.bat testDebugUnitTest assembleDebug` passed, including the static
contract regression checks. This does not prove that a remote Supabase project
has applied the migration or that an authenticated device has completed a live
RPC call; run those provider checks before production use.
This RPC does not create meal records and does not publish public Nutrition. A
later meal-entry projection may reference `nutrition_food_id` and snapshot the
values under the existing meal-record contract.
