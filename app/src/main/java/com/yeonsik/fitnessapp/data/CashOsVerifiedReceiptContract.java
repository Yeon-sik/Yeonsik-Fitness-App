package com.yeonsik.fitnessapp.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Versioned read contract for CashOS finance_get_verified_receipt_candidates_v1. */
public final class CashOsVerifiedReceiptContract {
    public static final String RPC_NAME = "finance_get_verified_receipt_candidates_v1";

    private CashOsVerifiedReceiptContract() {}

    public static final class ReceiptCandidateRow {
        public final String receiptId;
        public final String receiptItemId;
        public final String ledgerEntryId;
        public final String descriptionSnapshot;
        public final double quantity;
        public final String unit;
        public final int totalPriceKrw;
        public final String catalogProductId;
        public final String nutritionFoodId;

        public ReceiptCandidateRow(
                String receiptId, String receiptItemId, String ledgerEntryId,
                String descriptionSnapshot, double quantity, String unit,
                int totalPriceKrw, String catalogProductId, String nutritionFoodId
        ) {
            this.receiptId = receiptId;
            this.receiptItemId = receiptItemId;
            this.ledgerEntryId = ledgerEntryId;
            this.descriptionSnapshot = descriptionSnapshot;
            this.quantity = quantity;
            this.unit = unit;
            this.totalPriceKrw = totalPriceKrw;
            this.catalogProductId = catalogProductId;
            this.nutritionFoodId = nutritionFoodId;
        }
    }

    public static List<VerifiedReceiptItem> parse(List<ReceiptCandidateRow> rows) {
        List<VerifiedReceiptItem> result = new ArrayList<>();
        for (ReceiptCandidateRow row : rows) {
            result.add(new VerifiedReceiptItem(
                    row.receiptId, row.receiptItemId, row.ledgerEntryId,
                    row.descriptionSnapshot, row.quantity, row.unit, row.totalPriceKrw,
                    row.catalogProductId, row.nutritionFoodId,
                    VerifiedReceiptItem.STATUS_PENDING_CONSUMPTION));
        }
        return result;
    }

    public static List<VerifiedReceiptItem> parse(JSONArray rows) {
        List<ReceiptCandidateRow> candidates = new ArrayList<>();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                candidates.add(new ReceiptCandidateRow(
                        required(row, "receipt_id"),
                        required(row, "receipt_item_id"),
                        required(row, "ledger_entry_id"),
                        required(row, "description_snapshot"),
                        row.getDouble("quantity"),
                        row.has("unit") ? row.getString("unit") : "each",
                        row.getInt("total_price_krw"),
                        nullable(row, "pricetrace_catalog_product_id"),
                        nullable(row, "nutrition_food_id")
                ));
            }
        } catch (Exception error) {
            throw new IllegalArgumentException("CashOS 영수증 후보 계약이 유효하지 않습니다.", error);
        }
        return parse(candidates);
    }

    private static String required(JSONObject row, String key) {
        String value;
        try {
            value = row.getString(key).trim();
        } catch (Exception error) {
            throw new IllegalArgumentException("CashOS contract field missing: " + key, error);
        }
        if (value.isEmpty()) throw new IllegalArgumentException("CashOS contract field missing: " + key);
        return value;
    }

    private static String nullable(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return null;
        String value;
        try {
            value = row.getString(key).trim();
        } catch (Exception error) {
            throw new IllegalArgumentException("CashOS contract field invalid: " + key, error);
        }
        return value.isEmpty() ? null : value;
    }
}
