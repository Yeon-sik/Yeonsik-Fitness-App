package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Installs the versioned, device-local K-FIND catalog used by single-food search.
 *
 * <p>The rows are public locally so every signed-in account can reuse them, but they have no owner
 * and are never part of the private-user upload path. Unknown optional nutrients stay {@code NULL};
 * they are never converted to zero.</p>
 */
public final class VerifiedFoodCatalogSeed {
    public static final String SOURCE_TYPE = "kfind_official";
    public static final String ASSET_NAME = "verified_food_catalog_v3.json";
    public static final int EXPECTED_COUNT = 54;
    public static final int SEEDED_FOOD_COUNT = EXPECTED_COUNT;
    public static final String FOOD_ID_PREFIX = "kfind:";
    public static final String MEASUREMENT_POLICY =
            "per_item_preparation_edible_portion_100g";
    public static final String SOURCE_REFERENCE_PREFIX =
            "https://various.foodsafetykorea.go.kr/nutrient/general/food/detail.do"
                    + "?searchFoodCd=";

    private static final String SOURCE_REFERENCE_SUFFIX =
            "&searchMonthCd=AVG&searchRegionCd=ZZ";
    private static final String RAW_MACKEREL_CODE = "R211-021014001-1208";
    private static final String RAW_MACKEREL_SOURCE_REFERENCE_SUFFIX =
            "&searchMonthCd=AVG&searchRegionCd=33";
    private static final int ASSET_VERSION = 3;
    private static final Map<String, Set<String>> ALLOWED_MISSING_REQUIRED_FIELDS =
            buildAllowedMissingRequiredFields();
    private static final Map<String, FoodMetadata> METADATA_BY_CODE = buildMetadata();
    private static final Set<String> CURATED_FOOD_IDS = buildCuratedFoodIds();
    private static final Set<String> LEGACY_V1_CODES = buildLegacyV1Codes();

    private VerifiedFoodCatalogSeed() {
    }

    public static void seed(Context context, SQLiteDatabase database) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required for verified food seed.");
        }
        if (database == null) {
            throw new IllegalArgumentException("Database is required for verified food seed.");
        }

        Map<String, SeedFood> foods = loadFoods(context);
        boolean ownsTransaction = !database.inTransaction();
        if (ownsTransaction) {
            database.beginTransaction();
        }
        try {
            retireLegacyV1Foods(database);
            for (SeedFood food : foods.values()) {
                upsertFood(database, food);
            }
            if (ownsTransaction) {
                database.setTransactionSuccessful();
            }
        } finally {
            if (ownsTransaction) {
                database.endTransaction();
            }
        }
    }

    /** Stronger than source_type alone so a private/manual row cannot receive the official badge. */
    public static boolean isVerifiedSeedFood(NutritionFood food) {
        if (food == null || food.id == null || !CURATED_FOOD_IDS.contains(food.id)) {
            return false;
        }
        String code = food.id.substring(FOOD_ID_PREFIX.length());
        FoodMetadata metadata = METADATA_BY_CODE.get(code);
        String expectedPrepState = NutritionFood.prepStateForCookingMethod(
                metadata.cookingMethod
        );
        int expectedDataVersion = food.profile.hasAllRequired()
                ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                : NutritionFood.DATA_VERSION_MACROS_ONLY;
        return food.ownerId == null
                && metadata.name.equals(food.name)
                && metadata.category.equals(food.category)
                && SOURCE_TYPE.equals(food.sourceType)
                && sourceReference(code).equals(food.sourceReference)
                && Double.compare(food.basisAmount, 100.0) == 0
                && NutritionUnit.GRAM.equals(food.basisUnit)
                && expectedPrepState.equals(food.prepState)
                && metadata.cookingMethod.equals(food.cookingMethod)
                && food.sourceVersion != null
                && !food.sourceVersion.trim().isEmpty()
                && food.dataVersion == expectedDataVersion;
    }

    public static Set<String> curatedFoodIds() {
        return CURATED_FOOD_IDS;
    }

    private static void upsertFood(SQLiteDatabase database, SeedFood food) {
        ExistingRow existing = findExistingRow(database, food.id);
        if (existing != null && !existing.canBeUpdated()) {
            // Stable IDs must never overwrite a private/user-owned or unrelated public row.
            return;
        }

        String timestamp = OffsetDateTime.now().toString();
        ContentValues values = new ContentValues();
        values.put("id", food.id);
        values.putNull("owner_id");
        values.put("name", food.metadata.name);
        putNullable(values, "brand", food.metadata.brand);
        values.put("kind", NutritionFood.KIND_INGREDIENT);
        values.put("category", food.metadata.category);
        values.put("basis_amount", 100.0);
        values.put("basis_unit", NutritionUnit.GRAM);
        values.put(
                "prep_state",
                NutritionFood.prepStateForCookingMethod(food.metadata.cookingMethod)
        );
        values.put("cooking_method", food.metadata.cookingMethod);
        values.put("calories_kcal", food.profile.calories());
        values.put("protein_grams", food.profile.proteinGrams());
        values.put("carbs_grams", food.profile.carbsGrams());
        values.put("fat_grams", food.profile.fatGrams());
        putNullableDouble(values, "sodium_mg", food.profile.sodiumMg());
        putNullableDouble(values, "saturated_fat_grams", food.profile.saturatedFatGrams());
        putNullableDouble(values, "sugars_grams", food.profile.sugarsGrams());
        putNullableDouble(values, "fiber_grams", food.profile.fiberGrams());
        values.putNull("added_sugars_grams");
        putNullableDouble(values, "trans_fat_grams", food.profile.transFatGrams());
        putNullableDouble(values, "cholesterol_mg", food.profile.cholesterolMg());
        values.put("source_type", SOURCE_TYPE);
        values.put("source_reference", food.sourceReference);
        values.put("source_version", food.sourceVersion);
        values.put(
                "data_version",
                food.profile.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY
        );
        values.put("revision", 1);
        values.put("visibility", "public");
        values.put("created_at",
                existing != null && existing.createdAt != null
                        ? existing.createdAt
                        : timestamp);
        values.put("updated_at", timestamp);
        values.putNull("deleted_at");

        if (existing == null) {
            database.insertOrThrow("nutrition_foods", null, values);
        } else {
            database.update("nutrition_foods", values, "id = ?", new String[]{food.id});
        }
        replaceMicronutrients(database, food, timestamp);
    }

    private static void replaceMicronutrients(
            SQLiteDatabase database,
            SeedFood food,
            String timestamp
    ) {
        database.delete("nutrition_food_nutrients", "food_id = ?", new String[]{food.id});
        for (String nutrientCode : food.profile.knownMicronutrientCodes()) {
            Double amount = food.profile.value(nutrientCode);
            if (amount == null) {
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("id", food.id + ":" + nutrientCode);
            values.putNull("owner_id");
            values.put("food_id", food.id);
            values.put("nutrient_code", nutrientCode);
            values.put("amount", amount);
            values.put("unit", NutrientCode.unitOf(nutrientCode));
            values.put("created_at", timestamp);
            values.put("updated_at", timestamp);
            values.putNull("deleted_at");
            database.insertWithOnConflict(
                    "nutrition_food_nutrients",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        }
    }

    private static ExistingRow findExistingRow(SQLiteDatabase database, String foodId) {
        try (Cursor cursor = database.rawQuery(
                "SELECT owner_id, source_type, source_reference, created_at "
                        + "FROM nutrition_foods WHERE id = ? LIMIT 1",
                new String[]{foodId}
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new ExistingRow(
                    cursor.isNull(0) ? null : cursor.getString(0),
                    cursor.isNull(1) ? null : cursor.getString(1),
                    cursor.isNull(2) ? null : cursor.getString(2),
                    cursor.isNull(3) ? null : cursor.getString(3)
            );
        }
    }

    private static void retireLegacyV1Foods(SQLiteDatabase database) {
        String timestamp = OffsetDateTime.now().toString();
        for (String code : LEGACY_V1_CODES) {
            String foodId = FOOD_ID_PREFIX + code;
            String reference = sourceReference(code);

            ContentValues foodValues = new ContentValues();
            foodValues.put("updated_at", timestamp);
            foodValues.put("deleted_at", timestamp);
            int retired = database.update(
                    "nutrition_foods",
                    foodValues,
                    "id = ? AND owner_id IS NULL AND source_type = ? "
                            + "AND source_reference = ? AND deleted_at IS NULL",
                    new String[]{foodId, SOURCE_TYPE, reference}
            );
            if (retired == 0) {
                continue;
            }

            ContentValues nutrientValues = new ContentValues();
            nutrientValues.put("updated_at", timestamp);
            nutrientValues.put("deleted_at", timestamp);
            database.update(
                    "nutrition_food_nutrients",
                    nutrientValues,
                    "food_id = ? AND owner_id IS NULL AND deleted_at IS NULL",
                    new String[]{foodId}
            );
        }
    }

    private static Map<String, SeedFood> loadFoods(Context context) {
        if (METADATA_BY_CODE.size() != EXPECTED_COUNT) {
            throw new IllegalStateException(
                    "Verified food metadata must contain " + EXPECTED_COUNT + " unique rows."
            );
        }
        try (InputStream stream = context.getAssets().open(ASSET_NAME);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8)
             )) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            JSONObject root = new JSONObject(json.toString());
            if (root.optInt("version", 0) != ASSET_VERSION) {
                throw new IllegalStateException("Unsupported verified food asset version.");
            }
            if (!MEASUREMENT_POLICY.equals(root.optString("measurement_policy", ""))) {
                throw new IllegalStateException(
                        "Verified food asset must use per-item preparation and edible 100 g."
                );
            }
            JSONArray rows = root.optJSONArray("foods");
            if (rows == null || rows.length() != EXPECTED_COUNT) {
                throw new IllegalStateException(
                        "Verified food asset must contain exactly " + EXPECTED_COUNT + " rows."
                );
            }

            Map<String, SeedFood> foods = new LinkedHashMap<>();
            Set<String> seenCodes = new LinkedHashSet<>();
            for (int index = 0; index < rows.length(); index++) {
                SeedFood food = parseFood(rows.getJSONObject(index));
                if (!seenCodes.add(food.code)) {
                    throw new IllegalStateException(
                            "Duplicate verified food code: " + food.code
                    );
                }
                foods.put(food.code, food);
            }
            if (!seenCodes.equals(METADATA_BY_CODE.keySet())) {
                throw new IllegalStateException(
                        "Verified food asset codes do not match the catalog metadata."
                );
            }
            return foods;
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Unable to load verified food catalog.", error);
        }
    }

    private static SeedFood parseFood(JSONObject row) throws JSONException {
        String code = requireString(row, "food_code");
        FoodMetadata metadata = METADATA_BY_CODE.get(code);
        if (metadata == null) {
            throw new IllegalStateException("Unknown verified food code: " + code);
        }
        String officialName = requireString(row, "official_name");
        String prepState = requireString(row, "prep_state");
        String cookingMethod = requireString(row, "cooking_method");
        String expectedPrepState = NutritionFood.prepStateForCookingMethod(
                metadata.cookingMethod
        );
        if (!metadata.cookingMethod.equals(cookingMethod)
                || !expectedPrepState.equals(prepState)) {
            throw new IllegalStateException(
                    "Verified food preparation metadata does not match: " + code
            );
        }
        if (NutritionFood.COOKING_METHOD_RAW.equals(cookingMethod)
                && !officialName.contains("_생것")) {
            throw new IllegalStateException("Verified raw food source is not raw: " + code);
        }
        if (NutritionFood.COOKING_METHOD_GRILLED.equals(cookingMethod)
                && !officialName.contains("_구운것")) {
            throw new IllegalStateException(
                    "Verified grilled food source is not grilled: " + code
            );
        }
        String sourceVersion = requireString(row, "source_version");
        Double sodiumMg = requiredOrAllowedMissingNumber(row, "sodium_mg", code);
        Double saturatedFatGrams = requiredOrAllowedMissingNumber(
                row,
                "saturated_fat_grams",
                code
        );
        Double sugarsGrams = requiredOrAllowedMissingNumber(row, "sugars_grams", code);

        NutritionProfile.Builder profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, requiredNumber(row, "calories_kcal"))
                .value(NutritionProfile.PROTEIN_GRAMS, requiredNumber(row, "protein_grams"))
                .value(NutritionProfile.CARBS_GRAMS, requiredNumber(row, "carbs_grams"))
                .value(NutritionProfile.FAT_GRAMS, requiredNumber(row, "fat_grams"))
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(
                        NutritionProfile.SATURATED_FAT_GRAMS,
                        saturatedFatGrams
                )
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.FIBER_GRAMS, nullableNumber(row, "fiber_grams"))
                .value(NutritionProfile.TRANS_FAT_GRAMS, nullableNumber(row, "trans_fat_grams"))
                .value(NutritionProfile.CHOLESTEROL_MG, nullableNumber(row, "cholesterol_mg"));

        putMicronutrient(profile, row, NutrientCode.CALCIUM, "calcium_mg");
        putMicronutrient(profile, row, NutrientCode.IRON, "iron_mg");
        putMicronutrient(profile, row, NutrientCode.PHOSPHORUS, "phosphorus_mg");
        putMicronutrient(profile, row, NutrientCode.POTASSIUM, "potassium_mg");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_A, "vitamin_a_ug_rae");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_B1, "vitamin_b1_mg");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_B2, "vitamin_b2_mg");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_B3, "vitamin_b3_mg");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_C, "vitamin_c_mg");
        putMicronutrient(profile, row, NutrientCode.VITAMIN_D, "vitamin_d_ug");

        return new SeedFood(
                code,
                FOOD_ID_PREFIX + code,
                metadata,
                sourceReference(code),
                sourceVersion,
                profile.build()
        );
    }

    private static void putMicronutrient(
            NutritionProfile.Builder profile,
            JSONObject row,
            String nutrientCode,
            String jsonKey
    ) throws JSONException {
        Double amount = nullableNumber(row, jsonKey);
        if (amount != null) {
            profile.micronutrient(nutrientCode, amount);
        }
    }

    private static String requireString(JSONObject row, String key) {
        String value = row.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("Verified food row is missing " + key + ".");
        }
        return value;
    }

    private static Double requiredNumber(JSONObject row, String key) throws JSONException {
        Double value = nullableNumber(row, key);
        if (value == null) {
            throw new IllegalStateException("Verified food row is missing " + key + ".");
        }
        return value;
    }

    private static Double requiredOrAllowedMissingNumber(
            JSONObject row,
            String key,
            String code
    ) throws JSONException {
        Double value = nullableNumber(row, key);
        if (value != null) {
            return value;
        }
        Set<String> allowedFields = ALLOWED_MISSING_REQUIRED_FIELDS.get(code);
        if (allowedFields != null && allowedFields.contains(key)) {
            return null;
        }
        throw new IllegalStateException("Verified food row is missing " + key + ": " + code);
    }

    private static Double nullableNumber(JSONObject row, String key) throws JSONException {
        if (!row.has(key) || row.isNull(key)) {
            return null;
        }
        double value = row.getDouble(key);
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalStateException(
                    "Verified food nutrient must be finite and non-negative: " + key
            );
        }
        return value;
    }

    private static String sourceReference(String code) {
        String suffix = RAW_MACKEREL_CODE.equals(code)
                ? RAW_MACKEREL_SOURCE_REFERENCE_SUFFIX
                : SOURCE_REFERENCE_SUFFIX;
        return SOURCE_REFERENCE_PREFIX + code + suffix;
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, value);
        }
    }

    private static Map<String, FoodMetadata> buildMetadata() {
        Map<String, FoodMetadata> foods = new LinkedHashMap<>();

        addRaw(foods, "R209-008000501-0000", "닭가슴살(껍질 제거)",
                NutritionFood.CATEGORY_POULTRY);
        addRaw(foods, "R209-008000701-0000", "닭넓적다리",
                NutritionFood.CATEGORY_POULTRY);
        addRaw(foods, "R209-008006301-0000", "닭아랫다리(북채)",
                NutritionFood.CATEGORY_POULTRY);
        addRaw(foods, "R209-008000601-0000", "닭날개",
                NutritionFood.CATEGORY_POULTRY);

        addBeef(foods, "R209-027068701-0000", "소고기 안심(한우 1등급)");
        addBeef(foods, "R209-027069101-0000", "소고기 채끝(한우 1등급)");
        addBeef(foods, "R209-027068301-0000", "소고기 등심(한우 1등급)");
        addBeef(foods, "R209-027068401-0000", "소고기 목심(한우 1등급)");
        addBeef(foods, "R209-027068801-0000", "소고기 앞다리(한우 1등급)");
        addBeef(foods, "R209-027061601-0000", "소고기 우둔(한우 1등급)");
        addBeef(foods, "R209-027061301-0000", "소고기 설도(한우 1등급)");
        addBeef(foods, "R209-027068501-0000", "소고기 사태(한우 1등급)");
        addBeef(foods, "R209-027061801-0000", "소고기 양지(한우 1등급)");
        addBeef(foods, "R209-027068101-0000", "소고기 갈비(한우 1등급)");

        addPork(foods, "R209-014008701-0000", "돼지고기 안심");
        addPork(foods, "R209-014008301-0000", "돼지고기 등심");
        addPork(foods, "R209-014008401-0000", "돼지고기 목심");
        addPork(foods, "R209-014001001-0000", "돼지고기 뒷다리");
        addPork(foods, "R209-014008501-0000", "돼지고기 사태");
        addPork(foods, "R209-014001201-0000", "돼지고기 삼겹살");
        addPork(foods, "R209-014008101-0000", "돼지고기 갈비");
        addPork(foods, "R209-014008801-0000", "돼지고기 앞다리");

        addVegetable(foods, "R106-092000001-0000", "브로콜리");
        addVegetable(foods, "R106-115000001-0000", "시금치");
        addVegetable(foods, "R106-129000001-0000", "양배추");
        addVegetable(foods, "R106-101173701-0000", "적상추");
        addVegetable(foods, "R106-101033601-0000", "로메인상추");
        addVegetable(foods, "R106-148010001-0000", "오이(개량종)");
        addVegetable(foods, "R106-186000001-0000", "토마토");
        addVegetable(foods, "R106-186010001-0000", "방울토마토");
        addVegetable(foods, "R106-194008101-0000", "빨강 파프리카");
        addVegetable(foods, "R106-194008001-0000", "노랑 파프리카");
        addVegetable(foods, "R106-041007601-0000", "당근");
        addVegetable(foods, "R106-132000001-0000", "양파");
        addVegetable(foods, "R106-198050001-0000", "애호박");
        addVegetable(foods, "R107-023000001-0000", "새송이버섯");
        addVegetable(foods, "R107-025000001-0000", "팽이버섯");
        addVegetable(foods, "R107-027007101-0000", "표고버섯(전체)");
        addVegetable(foods, "R106-182000001-0000", "콩나물");
        addVegetable(foods, "R106-112000001-0000", "숙주나물");
        addVegetable(foods, "R106-122000001-0000", "아스파라거스");
        addVegetable(foods, "R106-198040001-0000", "단호박");
        addVegetable(foods, "R106-030000001-0000", "콜리플라워");

        addGrain(foods, "R101-008000301-0000", "백미(마른쌀)");
        addGrain(foods, "R101-008000501-0000", "현미(마른쌀)");
        addGrain(foods, "R101-025050101-0000", "쌀보리(도정)");
        addGrain(foods, "R101-008000701-0000", "흑미(마른쌀)");
        addGrain(foods, "R101-047002001-0000", "잡곡(혼합 원곡)");

        addSeafood(
                foods,
                "R211-201174001-0000",
                "연어회(홍연어·생것 기준)",
                NutritionFood.COOKING_METHOD_RAW
        );
        addSeafood(
                foods,
                "R211-059074001-0000",
                "참치회(참다랑어·생것 기준)",
                NutritionFood.COOKING_METHOD_RAW
        );
        addSeafood(
                foods,
                "R211-021014001-1208",
                "고등어회(생것·부산 평균)",
                NutritionFood.COOKING_METHOD_RAW
        );
        addSeafood(
                foods,
                "R211-201174050-0000",
                "연어구이(홍연어)",
                NutritionFood.COOKING_METHOD_GRILLED
        );
        addSeafood(
                foods,
                "R211-059074050-0000",
                "참치구이(참다랑어)",
                NutritionFood.COOKING_METHOD_GRILLED
        );
        addSeafood(
                foods,
                "R211-021014050-7300",
                "고등어구이(수입·일본 평균)",
                NutritionFood.COOKING_METHOD_GRILLED
        );

        return Collections.unmodifiableMap(foods);
    }

    private static Map<String, Set<String>> buildAllowedMissingRequiredFields() {
        Map<String, Set<String>> fieldsByCode = new LinkedHashMap<>();
        addAllowedMissingField(fieldsByCode, "R211-201174001-0000", "sugars_grams");
        addAllowedMissingField(fieldsByCode, "R211-059074001-0000", "sugars_grams");
        addAllowedMissingField(fieldsByCode, RAW_MACKEREL_CODE, "sodium_mg");
        addAllowedMissingField(fieldsByCode, RAW_MACKEREL_CODE, "saturated_fat_grams");
        addAllowedMissingField(fieldsByCode, "R211-201174050-0000", "sugars_grams");
        addAllowedMissingField(fieldsByCode, "R211-059074050-0000", "sugars_grams");
        addAllowedMissingField(fieldsByCode, "R211-021014050-7300", "sugars_grams");

        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : fieldsByCode.entrySet()) {
            immutable.put(
                    entry.getKey(),
                    Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue()))
            );
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static void addAllowedMissingField(
            Map<String, Set<String>> fieldsByCode,
            String code,
            String field
    ) {
        Set<String> fields = fieldsByCode.get(code);
        if (fields == null) {
            fields = new LinkedHashSet<>();
            fieldsByCode.put(code, fields);
        }
        fields.add(field);
    }

    private static Set<String> buildLegacyV1Codes() {
        Set<String> codes = new LinkedHashSet<>();
        Collections.addAll(
                codes,
                "R209-008000551-0000", "R209-008000751-0000",
                "R209-008006353-0000", "R209-008000653-0000",
                "R209-014008751-0000", "R209-014008351-0000",
                "R209-014008451-0000", "R209-014001051-0000",
                "R209-014008551-0000", "R209-014001251-0000",
                "R209-014008151-0000", "R209-014009446-0000",
                "R106-092000047-0000", "R106-115000047-0000",
                "R107-023000051-0000", "R107-025000051-0000",
                "R107-027000051-0000", "R106-182000046-0000",
                "R106-112000049-0000", "R106-122000047-0000",
                "R106-198040049-0000", "R106-030000047-0000",
                "D301-022000000-0001", "D101-050000000-0001",
                "D101-016000000-0001", "D101-032000000-0001",
                "P123-201020300-2989"
        );
        return Collections.unmodifiableSet(codes);
    }

    private static Set<String> buildCuratedFoodIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String code : METADATA_BY_CODE.keySet()) {
            ids.add(FOOD_ID_PREFIX + code);
        }
        return Collections.unmodifiableSet(ids);
    }

    private static void addBeef(Map<String, FoodMetadata> foods, String code, String name) {
        addRaw(foods, code, name, NutritionFood.CATEGORY_MEAT);
    }

    private static void addPork(
            Map<String, FoodMetadata> foods,
            String code,
            String name
    ) {
        addRaw(foods, code, name, NutritionFood.CATEGORY_MEAT);
    }

    private static void addVegetable(
            Map<String, FoodMetadata> foods,
            String code,
            String name
    ) {
        addRaw(foods, code, name, NutritionFood.CATEGORY_VEGETABLE);
    }

    private static void addGrain(
            Map<String, FoodMetadata> foods,
            String code,
            String name
    ) {
        addRaw(foods, code, name, NutritionFood.CATEGORY_GRAIN);
    }

    private static void addSeafood(
            Map<String, FoodMetadata> foods,
            String code,
            String name,
            String cookingMethod
    ) {
        addPrepared(foods, code, name, NutritionFood.CATEGORY_SEAFOOD, cookingMethod);
    }

    private static void addRaw(
            Map<String, FoodMetadata> foods,
            String code,
            String name,
            String category
    ) {
        addPrepared(foods, code, name, category, NutritionFood.COOKING_METHOD_RAW);
    }

    private static void addPrepared(
            Map<String, FoodMetadata> foods,
            String code,
            String name,
            String category,
            String cookingMethod
    ) {
        FoodMetadata previous = foods.put(
                code,
                new FoodMetadata(
                        name,
                        null,
                        category,
                        cookingMethod
                )
        );
        if (previous != null) {
            throw new IllegalStateException("Duplicate verified food metadata code: " + code);
        }
    }

    private static final class SeedFood {
        private final String code;
        private final String id;
        private final FoodMetadata metadata;
        private final String sourceReference;
        private final String sourceVersion;
        private final NutritionProfile profile;

        private SeedFood(
                String code,
                String id,
                FoodMetadata metadata,
                String sourceReference,
                String sourceVersion,
                NutritionProfile profile
        ) {
            this.code = code;
            this.id = id;
            this.metadata = metadata;
            this.sourceReference = sourceReference;
            this.sourceVersion = sourceVersion;
            this.profile = profile;
        }
    }

    private static final class FoodMetadata {
        private final String name;
        private final String brand;
        private final String category;
        private final String cookingMethod;

        private FoodMetadata(
                String name,
                String brand,
                String category,
                String cookingMethod
        ) {
            this.name = name;
            this.brand = brand;
            this.category = category;
            this.cookingMethod = cookingMethod;
        }
    }

    private static final class ExistingRow {
        private final String ownerId;
        private final String sourceType;
        private final String sourceReference;
        private final String createdAt;

        private ExistingRow(
                String ownerId,
                String sourceType,
                String sourceReference,
                String createdAt
        ) {
            this.ownerId = ownerId;
            this.sourceType = sourceType;
            this.sourceReference = sourceReference;
            this.createdAt = createdAt;
        }

        private boolean canBeUpdated() {
            return ownerId == null
                    && SOURCE_TYPE.equals(sourceType)
                    && sourceReference != null
                    && sourceReference.startsWith(SOURCE_REFERENCE_PREFIX);
        }
    }
}
