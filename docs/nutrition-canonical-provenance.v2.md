# Fitness canonical Nutrition provenance v2

Fitness owns the final Nutrition identity and the final `nutrition_foods` point-value row.
OCR App owns drafts, original-photo review, and user verification. It must call the
authenticated RPC below only after `p_user_verified=true`.

```text
POST {NUTRITION_SUPABASE_URL}/rest/v1/rpc/import_canonical_nutrition_v2
Authorization: Bearer <user access token>
apikey: <nutrition anon key>
Content-Type: application/json
```

`nutrition_foods` is a compatibility projection, not the canonical input record.
The complete canonical payload is in `nutrition_canonical_imports`; every required
nutrient is independently recorded in `nutrition_food_nutrient_provenance` with its
value, `value_status`, source type, evidence references, and (for estimates)
confidence/range. The existing `fitness-nutrition-draft.v1` / verified-import v1
flow remains available for older OCR App clients, but new clients should use this RPC.

## Canonical contracts

| `p_input_contract` | Meaning | `nutrition_foods.source_type` projection |
| --- | --- | --- |
| `nutrition-label.v1` | Value directly observed on a packaged-product Nutrition label | `product_label_ocr` |
| `food-estimate.v1` | Value estimated from a restaurant food/menu image or description | `food_image_estimate` |

The accepted per-nutrient `source_type` values are `product_label_ocr`,
`food_image_estimate`, `menu_reference`, and `manual`. Every required nutrient has:

```json
{
  "value": 110,
  "value_status": "observed",
  "source_type": "product_label_ocr",
  "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/nutrition-table"]
}
```

`evidence_refs` identify a photo, crop, OCR region, menu reference, or manual-review
artifact. They are not limited to OCR line IDs, so menu photos do not pretend to be
label-table OCR. All seven data-version-2 nutrients remain mandatory:
`calories_kcal`, `carbs_grams`, `protein_grams`, `fat_grams`, `sugars_grams`,
`saturated_fat_grams`, and `sodium_mg`.

## `nutrition-label.v1`: printed product facts

Every required nutrient must be `value_status=observed` and
`source_type=product_label_ocr`. Estimates, confidence ranges, and missing label
values are rejected; do not send zero for an unreadable label field. `basis_amount`
is the label basis, not package mass: a 300 g package labelled per 100 g sends
`p_basis_amount=100`, `p_basis_unit="g"`; package size can be retained in
`p_provenance.package_total_amount=300` and `package_total_unit="g"`.

```json
{
  "p_idempotency_key": "ocr:label-01J8:review-2",
  "p_input_contract": "nutrition-label.v1",
  "p_source_document_ref": "ocr-app://ingestion/01J8/document/2",
  "p_food_name": "닭가슴살 오리지널",
  "p_brand": "예시푸드",
  "p_category": "processed",
  "p_basis_amount": 100,
  "p_basis_unit": "g",
  "p_required_nutrients": {
    "calories_kcal": 110, "carbs_grams": 2, "protein_grams": 23,
    "fat_grams": 1, "sugars_grams": 1, "saturated_fat_grams": 0.3,
    "sodium_mg": 420
  },
  "p_nutrient_provenance": {
    "calories_kcal": {"value": 110, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/calories"]},
    "carbs_grams": {"value": 2, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/carbs"]},
    "protein_grams": {"value": 23, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/protein"]},
    "fat_grams": {"value": 1, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/fat"]},
    "sugars_grams": {"value": 1, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/sugars"]},
    "saturated_fat_grams": {"value": 0.3, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/saturated-fat"]},
    "sodium_mg": {"value": 420, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://ingestion/01J8/document/2/region/sodium"]}
  },
  "p_provenance": {"package_total_amount": 300, "package_total_unit": "g"},
  "p_user_verified": true,
  "p_pricetrace_identity": {
    "namespace": "pricetrace",
    "catalog_product_id": "11111111-1111-4111-8111-111111111111"
  }
}
```

## `food-estimate.v1`: restaurant image estimate

At least one required nutrient must be `estimated`; `product_label_ocr` is rejected
in this contract. Use `food_image_estimate`, `menu_reference`, or `manual` for the
per-nutrient source type. The point values become a private `external_menu` row with
the `food_image_estimate` projection source. `p_estimation_evidence.confidence` is
required; its optional per-nutrient `min` / `point` / `max` range remains in the
existing estimation evidence and is also retained with each nutrient provenance row.

```json
{
  "p_idempotency_key": "ocr:menu-photo-01J9:review-1",
  "p_input_contract": "food-estimate.v1",
  "p_source_document_ref": "ocr-app://ingestion/01J9/document/1",
  "p_food_name": "불고기 덮밥",
  "p_brand": "예시식당",
  "p_category": "recipe",
  "p_basis_amount": 1,
  "p_basis_unit": "serving",
  "p_required_nutrients": {
    "calories_kcal": 680, "carbs_grams": 86, "protein_grams": 29,
    "fat_grams": 22, "sugars_grams": 12, "saturated_fat_grams": 6,
    "sodium_mg": 1180
  },
  "p_nutrient_provenance": {
    "calories_kcal": {"value": 680, "value_status": "estimated", "source_type": "food_image_estimate", "evidence_refs": ["ocr-app://ingestion/01J9/document/1/photo"]},
    "carbs_grams": {"value": 86, "value_status": "estimated", "source_type": "food_image_estimate", "evidence_refs": ["ocr-app://ingestion/01J9/document/1/photo"]},
    "protein_grams": {"value": 29, "value_status": "estimated", "source_type": "food_image_estimate", "evidence_refs": ["ocr-app://ingestion/01J9/document/1/photo"]},
    "fat_grams": {"value": 22, "value_status": "estimated", "source_type": "food_image_estimate", "evidence_refs": ["ocr-app://ingestion/01J9/document/1/photo"]},
    "sugars_grams": {"value": 12, "value_status": "estimated", "source_type": "manual", "evidence_refs": ["ocr-app://ingestion/01J9/review/1"]},
    "saturated_fat_grams": {"value": 6, "value_status": "estimated", "source_type": "food_image_estimate", "evidence_refs": ["ocr-app://ingestion/01J9/document/1/photo"]},
    "sodium_mg": {"value": 1180, "value_status": "estimated", "source_type": "menu_reference", "evidence_refs": ["ocr-app://ingestion/01J9/menu-description"]}
  },
  "p_provenance": {"restaurant_name": "예시식당", "estimated": true},
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
    }
  },
  "p_user_verified": true
}
```

## PriceTrace, retries, and publication

Fitness owns `nutrition_food_id`; PriceTrace IDs are only references. A packaged
label may provide a resolved `catalog_product_id`. A restaurant estimate may provide
`dining-out-identity.v1` only when all four exact UUIDs are already resolved:
`restaurant_id`, `restaurant_location_id`, `restaurant_menu_id`, and
`catalog_product_id`. OCR App/GPT must never name-match or invent them. Existing
`product_nutrition_links` approval/immutability checks reject an import that would
attach a different active approved catalog product to the same Nutrition food.

The same authenticated owner and idempotency key return the original canonical
import only when the complete payload is identical; a changed reuse is rejected.
Different ingestions can update the same catalog identity without creating meal
records. The API always stores `visibility=private`; the existing owner publication
flow remains explicit and still requires the exact dining-out identity. This behavior
is unchanged by the separate `fitness-meal-component-estimate.v1` and
`fitness-meal-verified-import.v1` contracts documented in
`docs/fitness-meal-verified-import.v1.md`.

## Evidence boundary

This is repository-level contract documentation. Local unit tests and the Android
build validate source compatibility and static migration guards, but they do not
prove a remote Supabase migration, RLS execution, authenticated RPC request, or
PriceTrace publication. Run those provider checks before production use.
