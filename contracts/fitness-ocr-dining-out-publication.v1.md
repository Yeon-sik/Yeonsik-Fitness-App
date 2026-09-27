# Fitness OCR dining-out Nutrition publication v1

## Authority and intent

Fitness remains the authority for Nutrition values, provenance, ownership,
approved links, and public visibility. `import_canonical_nutrition_v2` and
`import_canonical_nutrition_v3` continue to create private Nutrition rows.
Calling a generic canonical import is not publication intent.

This contract is only for one final-reviewed OCR restaurant-menu estimate. An
authenticated OCR client must make the explicit publication RPC call below
after the canonical import and exact PriceTrace identity are both available.
The RPC accepts only a private, owner-owned row whose server-side canonical
import audit is `food-estimate.v1`, `user_verified=true`, and
`projection_source_type=food_image_estimate`. Product-label, external-reference,
manual, other-owner, deleted, and already-public rows are rejected.

## RPC

```text
POST {NUTRITION_SUPABASE_URL}/rest/v1/rpc/publish_verified_ocr_dining_out_nutrition_v1
Authorization: Bearer <Nutrition user access token>
apikey: <Nutrition anon key>
Content-Type: application/json
```

Named JSON request:

```json
{
  "p_idempotency_key": "ocr-v5:ingestion-01J:revision-3:menu-01J",
  "p_canonical_import_id": "00000000-0000-4000-8000-000000000001",
  "p_nutrition_food_id": "nutrition-food-id-returned-by-canonical-import",
  "p_restaurant_id": "00000000-0000-4000-8000-000000000002",
  "p_restaurant_location_id": "00000000-0000-4000-8000-000000000003",
  "p_restaurant_menu_id": "00000000-0000-4000-8000-000000000004",
  "p_catalog_product_id": "00000000-0000-4000-8000-000000000005"
}
```

The `canonical_import_id` is required provenance, not a client assertion of
verification. Fitness resolves it under the logged-in owner and checks that it
belongs to the supplied Nutrition food. Each PriceTrace ID is required; null or
partial identity is rejected. If the canonical import or its initial
`source_reference` already contains any of the four exact IDs, every present
ID must match the request.

Fitness Nutrition and PriceTrace use separate databases, so this RPC cannot
independently query PriceTrace to prove that four otherwise-unrecorded UUIDs
belong to the same Restaurant/Menu. OCR must pass the exact IDs returned by the
authenticated PriceTrace resolution response. The server rejects malformed
UUIDs and contradictions with any identity already recorded in the canonical
import, but it does not infer or fuzzy-match identities.

## Atomic behavior

One RPC transaction performs these existing owner-scoped operations in order:

1. `attach_dining_out_menu_identity_v1`
2. `attach_dining_out_menu_nutrition_link_v1` and verify its status is
   `approved`
3. `set_dining_out_menu_publication_v1(..., true)`
4. Persist the owner-scoped idempotency request and stable response

Any error aborts the SQL statement and rolls back all four writes. A failed
attempt leaves no new identity, link, publication event, or idempotency result.
It does not change canonical import visibility defaults or publish unrelated
OCR Nutrition.

## Response

PostgREST returns a one-row array. The row includes:

| Field | Meaning |
| --- | --- |
| `canonical_import_id` | Verified canonical import provenance used by this call |
| `nutrition_food_id` | Fitness-owned Nutrition row |
| `restaurant_id`, `restaurant_location_id`, `restaurant_menu_id`, `catalog_product_id` | Exact PriceTrace IDs accepted by the transaction |
| `nutrition_link_id`, `nutrition_link_revision` | Approved catalog/menu Nutrition link |
| `visibility` | `public` only after successful publication |
| `food_revision` | Nutrition row revision after identity attachment |
| `publication_revision`, `published_at` | Audited publication result |
| `replayed` | `false` for first success; `true` when returning the saved result |

The key is scoped to the authenticated Nutrition owner. Replaying the same key
and identical request returns the original successful result, even if the row
is changed later. Reusing that key with any changed field returns a conflict.
Different owners have independent key spaces.

## OCR V5 call order

1. The user completes final review of one dining-out Nutrition item in the
   `.yeonsik` ingestion. That reviewed item is the publication intent; do not
   publish other Nutrition items from the same file implicitly.
2. Resolve the restaurant, location, menu, and catalog product with
   PriceTrace. Keep the exact four IDs returned by PriceTrace; do not build
   them from names or client-side matching.
3. Call `import_canonical_nutrition_v2` or `import_canonical_nutrition_v3`
   with `p_input_contract=food-estimate.v1` and `p_user_verified=true`. Retain
   the returned `canonical_import_id` and `nutrition_food_id`. Confirm the
   import response remains `visibility=private`.
4. Call `publish_verified_ocr_dining_out_nutrition_v1` with an idempotency key
   stable for this reviewed ingestion revision and menu item, both returned
   import IDs, and all four exact PriceTrace IDs.
5. Treat only a successful row with `visibility=public`, matching IDs,
   `publication_revision`, and `published_at` as completion. Retry the same
   request with the same key after a transport failure. Do not ask the user to
   open Fitness or use its manual publication screen.

If step 4 fails, the canonical Nutrition row remains private and the same
publication request can be retried. Import-only calls never publish.
