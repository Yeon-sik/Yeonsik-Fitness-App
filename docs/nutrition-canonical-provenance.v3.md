# Fitness canonical Nutrition hierarchy v3

Fitness owns the authoritative `import_canonical_nutrition_v3` boundary. This
is an additive endpoint for four explicit packaged-product hierarchy facts and
the third canonical nutrition input contract. The accepted `p_input_contract`
values are `nutrition-label.v1`, `food-estimate.v1`, and
`external-reference.v1`; each keeps a distinct source and evidence meaning.

## Endpoint

```text
POST {NUTRITION_SUPABASE_URL}/rest/v1/rpc/import_canonical_nutrition_v3
Authorization: Bearer <Nutrition user access token>
apikey: <Nutrition anon key>
Content-Type: application/json
```

The RPC is authenticated and writes a private Nutrition projection. The
PostgREST request is a JSON object whose property names are the SQL parameter
names below; positional JSON arrays are not part of this contract.

## Authoritative RPC parameter contract

```sql
public.import_canonical_nutrition_v3(
    p_idempotency_key text,
    p_input_contract text,
    p_source_document_ref text,
    p_food_name text,
    p_brand text,
    p_category text,
    p_basis_amount numeric,
    p_basis_unit text,
    p_required_nutrients jsonb,
    p_nutrient_provenance jsonb,
    p_optional_nutrients jsonb default '{}'::jsonb,
    p_provenance jsonb default '{}'::jsonb,
    p_user_verified boolean default false,
    p_pricetrace_identity jsonb default null,
    p_estimation_evidence jsonb default null,
    p_manufacturer_name text default null,
    p_brand_name text default null,
    p_sub_brand_name text default null,
    p_product_name text default null
)
```

The first fifteen parameters retain the canonical v2 order and meanings. The
last four are nullable scalar fields and must not be replaced by an array:

| Parameter | Meaning | Null/blank behavior |
| --- | --- | --- |
| `p_manufacturer_name` | Explicit printed manufacturer fact | blank is stored as `NULL` |
| `p_brand_name` | Explicit printed brand fact | blank is stored as `NULL` |
| `p_sub_brand_name` | Explicit printed sub-brand fact | blank is stored as `NULL` |
| `p_product_name` | Explicit printed product-name fact | blank is stored as `NULL` |

Missing hierarchy levels remain `NULL`. They are never copied from
`p_food_name`, `p_brand`, `p_pricetrace_identity`, or another hierarchy level.

`p_brand` is the legacy compatibility alias. For a `nutrition-label.v1` call,
when both `p_brand` and `p_brand_name` are nonblank, they must match exactly
after trimming; otherwise the RPC rejects the request. A matching alias is
accepted, and the explicit `p_brand_name` field remains independently stored.

`food-estimate.v1` is the restaurant estimate path. Its four hierarchy
parameters must be `NULL` (or omitted so their defaults apply); any nonblank
packaged-product hierarchy value is rejected. Restaurant name/menu facts stay
in the existing `p_brand`, `p_food_name`, `p_provenance`, and estimation
evidence fields.

## Three input contract semantics

| Contract | Source semantics | Required nutrient evidence | Projection meaning |
| --- | --- | --- | --- |
| `nutrition-label.v1` | Nutrition values read from a product label by OCR or equivalent label evidence | All seven values are `observed` with `source_type=product_label_ocr`; the contract is not estimated | `nutrition_foods.source_type=product_label_ocr`; the existing label semantics are unchanged |
| `food-estimate.v1` | Restaurant/menu nutrition estimated from food or menu evidence | At least one value is `estimated`, with confidence/range evidence; product-label evidence is rejected | `nutrition_foods.source_type=food_image_estimate`; hierarchy remains empty and the existing estimate semantics are unchanged |
| `external-reference.v1` | Nutrition values obtained from a public manufacturer, brand, or official distributor reference | All seven values are `observed` with `source_type=external_reference`; every nutrient evidence list carries the same public URL; it is not an estimate | `nutrition_foods.source_type=external_reference`, `source_reference` is the public URL, and `source_version` is `external-nutrition-lookup.v1` |

For `external-reference.v1`, `p_provenance.source_reference` and
`p_source_document_ref` must contain the same trimmed public HTTP(S) URL.
The RPC preserves that URL and the parser/source version in the canonical audit,
nutrition projection, legacy compatibility provenance, and per-nutrient
provenance rows. It does not convert the basis to 100 g or 100 ml: the
upstream `p_basis_amount` and `p_basis_unit` are stored as supplied.

External-reference import still requires an explicit `p_user_verified=true`
from the caller; Fitness does not promote an unreviewed external source to
`user_verified`. It also rejects client-supplied PriceTrace identity for this
contract, creates no consumption row, and does not resolve or create Product
identity. Product-to-nutrition links remain under the existing
PriceTrace/Fitness authority flow.

## Request examples

A packaged-product label uses the existing nutrient contract plus explicit
scalar hierarchy fields:

```json
{
  "p_idempotency_key": "ocr:label:review-2",
  "p_input_contract": "nutrition-label.v1",
  "p_source_document_ref": "ocr-app://document/label/review-2",
  "p_food_name": "닭가슴살 오리지널",
  "p_brand": "예시푸드",
  "p_category": "processed",
  "p_basis_amount": 100,
  "p_basis_unit": "g",
  "p_required_nutrients": { "calories_kcal": 110, "carbs_grams": 2, "protein_grams": 23, "fat_grams": 1, "sugars_grams": 1, "saturated_fat_grams": 0.3, "sodium_mg": 420 },
  "p_nutrient_provenance": { "calories_kcal": { "value": 110, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/calories"] }, "carbs_grams": { "value": 2, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/carbs"] }, "protein_grams": { "value": 23, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/protein"] }, "fat_grams": { "value": 1, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/fat"] }, "sugars_grams": { "value": 1, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/sugars"] }, "saturated_fat_grams": { "value": 0.3, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/saturated-fat"] }, "sodium_mg": { "value": 420, "value_status": "observed", "source_type": "product_label_ocr", "evidence_refs": ["ocr-app://document/label/review-2/sodium"] } },
  "p_user_verified": true,
  "p_manufacturer_name": "예시식품 주식회사",
  "p_brand_name": "예시푸드",
  "p_sub_brand_name": "프로틴 라인",
  "p_product_name": "닭가슴살 오리지널"
}
```

A restaurant estimate uses the same existing `food-estimate.v1` contract and
keeps hierarchy explicitly empty:

```json
{
  "p_idempotency_key": "ocr:restaurant:review-1",
  "p_input_contract": "food-estimate.v1",
  "p_source_document_ref": "ocr-app://document/restaurant/review-1",
  "p_food_name": "불고기 덮밥",
  "p_brand": "예시식당",
  "p_category": "recipe",
  "p_basis_amount": 1,
  "p_basis_unit": "serving",
  "p_required_nutrients": { "...": "the existing seven required values" },
  "p_nutrient_provenance": { "...": "the existing per-nutrient evidence" },
  "p_provenance": { "restaurant_name": "예시식당", "estimated": true },
  "p_estimation_evidence": { "confidence": 0.62 },
  "p_user_verified": true,
  "p_manufacturer_name": null,
  "p_brand_name": null,
  "p_sub_brand_name": null,
  "p_product_name": null
}
```

The abbreviated objects in the restaurant example mean the unchanged v2
nutrient/evidence objects, not a relaxed validation rule. All seven required
nutrient keys and evidence rules still apply.

An external reference uses the producer's public URL as the source document
reference and preserves the lookup provenance:

```json
{
  "p_idempotency_key": "ocr:text-lookup:external-test-cereal-1",
  "p_input_contract": "external-reference.v1",
  "p_source_document_ref": "https://nutrition.example.com/products/test-cereal",
  "p_food_name": "Test cereal",
  "p_brand": "Test brand",
  "p_category": "processed",
  "p_basis_amount": 100,
  "p_basis_unit": "g",
  "p_required_nutrients": { "calories_kcal": 380, "protein_grams": 10, "carbs_grams": 70, "fat_grams": 5, "sodium_mg": 100, "saturated_fat_grams": 1, "sugars_grams": 12 },
  "p_nutrient_provenance": { "...": "seven observed external_reference entries, each referencing the same URL" },
  "p_provenance": {
    "parser_version": "external-nutrition-lookup.v1",
    "source_type": "external_reference",
    "source_reference": "https://nutrition.example.com/products/test-cereal",
    "source_version": "external-nutrition-lookup.v1",
    "canonical_input_contract": "external-reference.v1",
    "estimated": false
  },
  "p_user_verified": true,
  "p_manufacturer_name": "Test Foods",
  "p_brand_name": "Test brand",
  "p_sub_brand_name": null,
  "p_product_name": "Test cereal"
}
```

The seven nutrient values and basis fields above come from the OCR-App
representative `yeonsik-ocr.v2` text-lookup fixture; the fixture's optional
nutrients and nested producer shape are preserved in the Fitness integration
test.

## Persistence, idempotency, and read contract

The four normalized values travel through the following path without
inference:

1. The RPC stores them on the compatibility projection
   `nutrition_foods.manufacturer_name`, `brand_name`, `sub_brand_name`, and
   `product_name`.
2. The canonical audit row stores the same values in
   `nutrition_canonical_imports.request_payload` and records a SHA-256
   `hierarchy_fingerprint` over the ordered four-value JSON array.
3. The same owner and idempotency key replay only when the complete normalized
   request payload is identical. A changed hierarchy value is a conflict and
   cannot overwrite the original import.
4. `get_nutrition_read_v3(text default null)` returns
   `nutrition-read.v2` fields plus the same four nullable hierarchy fields.
   Its response `contract_version` is `nutrition-read.v3`.

The read contract is versioned independently from input semantics. The RPC
does not create or infer a PriceTrace UUID. An optional exact
`catalog_product_id` remains a separate PriceTrace reference/link, with no
cross-database foreign key or identity ownership transfer. For
`external-reference.v1`, the RPC deliberately leaves that identity null.

Existing `import_verified_nutrition_v1`, `import_canonical_nutrition_v2`,
`nutrition-label.v1`, and `food-estimate.v1` callers remain unchanged.
The v3 endpoint now accepts those two existing contracts plus
`external-reference.v1`; it does not accept `nutrition-label.v3`,
`food-estimate.v3`, or `p_category_hierarchy`.
