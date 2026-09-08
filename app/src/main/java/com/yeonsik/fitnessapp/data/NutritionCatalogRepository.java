package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.NutritionFoodNutrientsRoomEntity;
import com.yeonsik.fitnessapp.core.database.NutritionFoodComponentsRoomEntity;
import com.yeonsik.fitnessapp.core.database.NutritionFoodsRoomEntity;
import com.yeonsik.fitnessapp.core.database.NutritionRoomDao;
import com.yeonsik.fitnessapp.core.database.DiningOutMenuComponentLinksRoomEntity;
import com.yeonsik.fitnessapp.core.database.ProductNutritionLinksRoomEntity;
import com.yeonsik.fitnessapp.core.database.PricetraceProductCacheRoomEntity;

import com.yeonsik.fitnessapp.config.AccountOwnerPolicy;
import com.yeonsik.fitnessapp.config.SupabaseConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Local-first food catalog and recipe repository.
 *
 * <p>The app can record and search foods without a network connection. When a Supabase
 * connection is configured, public catalog rows are pulled and authenticated user rows are
 * pushed/pulled through the REST API.</p>
 *
 * <p>이 저장소는 <b>음식·레시피·영양성분만</b> 다룬다. 원격 테이블은 FitnessApp 전용
 * Nutrition Supabase 프로젝트에 있으며, Personal OS 공통 DB의 meal_records 같은 사용자
 * 섭취 기록은 이 카탈로그에 절대 들어가지 않는다.</p>
 */
public final class NutritionCatalogRepository implements com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi {
    /** 영양 전용 DB의 카탈로그 테이블. 공통 사용자 기록 테이블은 여기 들어올 수 없다. */
    static final List<String> CATALOG_TABLES = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "nutrition_foods",
                    "nutrition_food_nutrients",
                    "nutrition_food_components",
                    "product_nutrition_links"
            )
    );

    private static final String[] FOOD_COLUMNS = {
            "id",
            "owner_id",
            "name",
            "brand",
            "manufacturer_name",
            "brand_name",
            "sub_brand_name",
            "product_name",
            "package_amount",
            "package_unit",
            "package_count",
            "kind",
            "category",
            "basis_amount",
            "basis_unit",
            "prep_state",
            "cooking_method",
            "calories_kcal",
            "protein_grams",
            "carbs_grams",
            "fat_grams",
            "sodium_mg",
            "saturated_fat_grams",
            "sugars_grams",
            "fiber_grams",
            "added_sugars_grams",
            "trans_fat_grams",
            "cholesterol_mg",
            "source_type",
            "source_reference",
            "source_version",
            "data_version",
            "revision"
    };

    private static final String[] FOOD_SYNC_COLUMNS = syncColumns();

    private static final String[] NUTRIENT_SYNC_COLUMNS = {
            "id", "owner_id", "food_id", "nutrient_code", "amount", "unit",
            "created_at", "updated_at", "deleted_at"
    };

    private static final String[] COMPONENT_SYNC_COLUMNS = {
            "id", "owner_id", "parent_food_id", "child_food_id", "quantity", "unit",
            "order_index", "created_at", "updated_at", "deleted_at"
    };

    static final String[] PRODUCT_LINK_SYNC_COLUMNS = {
            "id", "owner_id", "nutrition_food_id", "catalog_product_id", "standard_product_id", "status",
            "source_type", "proposal_reference", "product_contract_version",
            "catalog_product_revision", "catalog_content_amount", "catalog_content_unit",
            "catalog_package_count", "revision",
            "reviewed_at", "created_at", "updated_at", "deleted_at"
    };

    private static final int VERIFIED_FOOD_SEARCH_LIMIT_MAX = 50;
    private static final String VERIFIED_FOOD_ID_PREFIX =
            VerifiedFoodCatalogSeed.FOOD_ID_PREFIX + "%";
    private static final String VERIFIED_FOOD_SOURCE_REFERENCE_PREFIX =
            VerifiedFoodCatalogSeed.SOURCE_REFERENCE_PREFIX + "%";
    private static final String DINING_OUT_MENU_SOURCE_TYPE = "manual_estimate";
    private static final String OCR_DINING_OUT_MENU_SOURCE_TYPE = "food_image_estimate";
    private static final String DINING_OUT_OPTION_SOURCE_TYPE = "manual_option";
    private static final int SAVED_DINING_OUT_OPTION_RESULT_LIMIT_MAX = 50;
    private static final int PACKAGED_PRODUCT_RESULT_LIMIT_MAX = 50;

    private final FitnessRoomDatabase roomDatabase;
    private final NutritionRoomDao nutritionDao;
    /** Transitional sync adapter; local catalog CRUD uses nutritionDao. */
    private final FitnessDatabaseConnection database;
    private volatile String userId;
    private volatile SupabaseConfig supabaseConfig;

    public NutritionCatalogRepository(
            FitnessDatabaseHelper dbHelper,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this(
                FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()),
                FitnessDatabaseConnection.fromLegacy(dbHelper),
                userId,
                supabaseConfig
        );
    }

    public NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this(
                roomDatabase,
                FitnessDatabaseConnection.fromRoom(roomDatabase),
                userId,
                supabaseConfig
        );
    }

    private NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            FitnessDatabaseConnection database,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this.roomDatabase = roomDatabase;
        this.nutritionDao = roomDatabase.nutritionRoomDao();
        this.database = database;
        this.userId = normalizeUserId(userId);
        this.supabaseConfig = supabaseConfig == null ? SupabaseConfig.empty() : supabaseConfig;
    }

    public NutritionCatalogRepository(
            FitnessDatabaseConnection database,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this(
                FitnessRoomDatabaseProvider.get(database.applicationContext()),
                database,
                userId,
                supabaseConfig
        );
    }

    public NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            android.content.Context context,
            String userId,
            SupabaseConfig supabaseConfig
    ) {
        this(
                roomDatabase,
                FitnessDatabaseConnection.fromRoom(roomDatabase, context),
                userId,
                supabaseConfig
        );
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public void normalizeLocalUserId(String nextUserId) {
        String normalizedNextUserId = normalizeUserId(nextUserId);
        String previousUserId = userId;
        if (AccountOwnerPolicy.shouldClaimLocalRows(
                previousUserId,
                normalizedNextUserId
        )) {
            FitnessDatabaseConnection database = this.database;
            database.beginTransaction();
            try {
                resolveApprovedLinkClaimConflicts(database, normalizedNextUserId);
                ContentValues values = new ContentValues();
                values.put("owner_id", normalizedNextUserId);
                for (String table : CATALOG_TABLES) {
                    database.update(
                            table,
                            values,
                            "owner_id = ?",
                            new String[]{SupabaseConfig.DEFAULT_USER_ID}
                    );
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
        userId = normalizedNextUserId;
    }

    private void resolveApprovedLinkClaimConflicts(
            FitnessDatabaseConnection database,
            String nextUserId
    ) {
        List<String[]> conflicts = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(
                "SELECT source.id, source.updated_at, target.id, target.updated_at " +
                        "FROM product_nutrition_links source " +
                        "INNER JOIN product_nutrition_links target " +
                        "ON target.nutrition_food_id = source.nutrition_food_id " +
                        "AND target.owner_id = ? AND target.status = 'approved' " +
                        "AND target.deleted_at IS NULL " +
                        "WHERE source.owner_id = ? AND source.status = 'approved' " +
                        "AND source.deleted_at IS NULL",
                new String[]{nextUserId, SupabaseConfig.DEFAULT_USER_ID}
        )) {
            while (cursor.moveToNext()) {
                conflicts.add(new String[]{
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3)
                });
            }
        }

        for (String[] conflict : conflicts) {
            boolean localWins = compareVersions(conflict[1], conflict[3]) > 0;
            String losingId = localWins ? conflict[2] : conflict[0];
            String winningTimestamp = localWins ? conflict[1] : conflict[3];
            database.execSQL(
                    "UPDATE product_nutrition_links SET deleted_at = ?, updated_at = ?, " +
                            "revision = revision + 1 WHERE id = ? AND deleted_at IS NULL",
                    new Object[]{winningTimestamp, winningTimestamp, losingId}
            );
        }
    }

    public void setSupabaseConfig(SupabaseConfig supabaseConfig) {
        this.supabaseConfig = supabaseConfig == null ? SupabaseConfig.empty() : supabaseConfig;
    }

    @Override
    public List<NutritionFood> searchFoods(String query) {
        String term = query == null ? "" : query.trim();
        String like = "%" + term + "%";
        return buildFoods(
                nutritionDao.searchFoods(userId, DINING_OUT_OPTION_SOURCE_TYPE, like)
        );
    }

    /**
     * Searches reusable packaged-food products, returning one representative package per
     * canonical product. Package rows remain available through packagedFoodVariants().
     */
    public List<NutritionFood> searchPackagedFoods(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, PACKAGED_PRODUCT_RESULT_LIMIT_MAX));
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<NutritionFood> candidates = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.visibleFoods(userId)) {
            if (!NutritionFood.KIND_EXTERNAL_MENU.equals(entity.getKind())
                    || "manual_estimate".equalsIgnoreCase(entity.getSourceType())
                    || "food_image_estimate".equalsIgnoreCase(entity.getSourceType())
                    || DINING_OUT_OPTION_SOURCE_TYPE.equalsIgnoreCase(entity.getSourceType())
                    || !containsAny(entity, term)) {
                continue;
            }
            candidates.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                    .get(entity.getId())));
        }
        Map<String, NutritionFood> representatives = new LinkedHashMap<>();
        for (NutritionFood candidate : candidates) {
            if (!candidate.isPackagedFood()) {
                continue;
            }
            representatives.putIfAbsent(canonicalPackagedProductKey(candidate), candidate);
            if (representatives.size() >= safeLimit) {
                break;
            }
        }
        return new ArrayList<>(representatives.values());
    }

    public List<NutritionFood> searchPackagedFoods(String query) {
        return searchPackagedFoods(query, PACKAGED_PRODUCT_RESULT_LIMIT_MAX);
    }

    /** Source-compatible name for callers that treat saved products as a catalog picker. */
    public List<NutritionFood> savedPackagedFoods(String query, int limit) {
        return searchPackagedFoods(query, limit);
    }

    public List<NutritionFood> savedPackagedFoods(String query) {
        return searchPackagedFoods(query, PACKAGED_PRODUCT_RESULT_LIMIT_MAX);
    }

    /**
     * Returns the package/variant rows belonging to one canonical packaged product. Duplicate
     * rows for the same package are represented by their newest row because readFoods is ordered
     * by updated_at descending.
     */
    public List<NutritionFood> packagedFoodVariants(NutritionFood product) {
        if (product == null || !product.isPackagedFood()) {
            return new ArrayList<>();
        }
        String canonicalKey = canonicalPackagedProductKey(product);
        List<NutritionFood> candidates = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.visibleFoods(userId)) {
            if (NutritionFood.KIND_EXTERNAL_MENU.equals(entity.getKind())
                    && !"manual_estimate".equalsIgnoreCase(entity.getSourceType())
                    && !"food_image_estimate".equalsIgnoreCase(entity.getSourceType())
                    && !DINING_OUT_OPTION_SOURCE_TYPE.equalsIgnoreCase(entity.getSourceType())) {
                candidates.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                        .get(entity.getId())));
            }
        }
        Map<String, NutritionFood> variants = new LinkedHashMap<>();
        for (NutritionFood candidate : candidates) {
            if (!candidate.isPackagedFood()
                    || !canonicalKey.equals(canonicalPackagedProductKey(candidate))) {
                continue;
            }
            variants.putIfAbsent(packagedVariantKey(candidate), candidate);
        }
        return new ArrayList<>(variants.values());
    }

    /** Canonical product identity; package amount/count are deliberately excluded. */
    public String canonicalPackagedProductKey(NutritionFood food) {
        if (food == null) {
            return "unresolved|product";
        }
        ProductNutritionLink link = approvedProductLink(food.id);
        if (link != null) {
            if (link.standardProductId != null && !link.standardProductId.trim().isEmpty()) {
                return "pricetrace|standard|" + normalizeIdentityText(link.standardProductId);
            }
            if (link.catalogProductId != null && !link.catalogProductId.trim().isEmpty()) {
                return "pricetrace|catalog|" + normalizeIdentityText(link.catalogProductId);
            }
        }
        return "local|manufacturer|" + normalizeIdentityText(food.manufacturerName)
                + "|brand|" + normalizeIdentityText(
                food.brandName == null ? food.brand : food.brandName
        )
                + "|sub_brand|" + normalizeIdentityText(food.subBrandName)
                + "|product|" + normalizeIdentityText(
                food.productName == null ? food.name : food.productName
        );
    }

    private static String packagedVariantKey(NutritionFood food) {
        String amount = food.packageAmount == null
                ? ""
                : NutritionCalculator.trim(food.packageAmount);
        String unit = normalizeIdentityText(food.packageUnit);
        String count = food.packageCount == null ? "" : String.valueOf(food.packageCount);
        if (amount.isEmpty() && unit.isEmpty() && count.isEmpty()) {
            return "basis|" + NutritionCalculator.trim(food.basisAmount)
                    + "|" + normalizeIdentityText(food.basisUnit);
        }
        return "package|" + amount + "|" + unit + "|" + count;
    }

    private static String normalizeIdentityText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(NutritionFoodsRoomEntity entity, String term) {
        if (term == null || term.isEmpty()) {
            return true;
        }
        String[] values = {
                entity.getName(),
                entity.getBrand(),
                entity.getManufacturerName(),
                entity.getBrandName(),
                entity.getSubBrandName(),
                entity.getProductName()
        };
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(term)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NutritionFood findFoodById(String foodId) {
        String normalizedId = requireName(foodId);
        NutritionFoodsRoomEntity food = nutritionDao.visibleFood(normalizedId, userId);
        return food == null ? null : buildFood(food, loadMicronutrientsRoom(singleton(normalizedId))
                .get(normalizedId));
    }

    public List<NutritionFood> searchVerifiedFoods(String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, VERIFIED_FOOD_SEARCH_LIMIT_MAX));
        String term = query == null ? "" : query.trim();
        String like = "%" + term + "%";
        Set<String> curatedIds = VerifiedFoodCatalogSeed.curatedFoodIds();
        StringBuilder curatedPlaceholders = new StringBuilder();
        for (int index = 0; index < curatedIds.size(); index++) {
            if (index > 0) {
                curatedPlaceholders.append(", ");
            }
            curatedPlaceholders.append('?');
        }
        List<String> arguments = new ArrayList<>();
        arguments.add(NutritionFood.KIND_INGREDIENT);
        arguments.add(VERIFIED_FOOD_ID_PREFIX);
        arguments.add(VerifiedFoodCatalogSeed.SOURCE_TYPE);
        arguments.add(VERIFIED_FOOD_SOURCE_REFERENCE_PREFIX);
        arguments.add(VerifiedFoodCatalogSeed.RICE_SOURCE_TYPE);
        arguments.add(VerifiedFoodCatalogSeed.RICE_SOURCE_REFERENCE);
        arguments.addAll(curatedIds);
        arguments.add(like);
        arguments.add(like);
        List<NutritionFood> candidates = new ArrayList<>();
        String verifiedTerm = term.toLowerCase(Locale.ROOT);
        for (NutritionFoodsRoomEntity entity : nutritionDao.verifiedFoods(
                NutritionFood.KIND_INGREDIENT,
                VERIFIED_FOOD_ID_PREFIX,
                VerifiedFoodCatalogSeed.SOURCE_TYPE,
                VERIFIED_FOOD_SOURCE_REFERENCE_PREFIX,
                VerifiedFoodCatalogSeed.RICE_SOURCE_TYPE,
                VerifiedFoodCatalogSeed.RICE_SOURCE_REFERENCE
        )) {
            if (!curatedIds.contains(entity.getId())) {
                continue;
            }
            String name = entity.getName() == null ? "" : entity.getName().toLowerCase(Locale.ROOT);
            String brand = entity.getBrand() == null ? "" : entity.getBrand().toLowerCase(Locale.ROOT);
            if (name.contains(verifiedTerm) || brand.contains(verifiedTerm)) {
                candidates.add(buildFood(
                        entity,
                        loadMicronutrientsRoom(singleton(entity.getId())).get(entity.getId())
                ));
            }
            if (candidates.size() >= safeLimit) {
                break;
            }
        }
        List<NutritionFood> verified = new ArrayList<>();
        for (NutritionFood candidate : candidates) {
            if (VerifiedFoodCatalogSeed.isVerifiedSeedFood(candidate)) {
                verified.add(candidate);
            }
        }
        return verified;
    }

    /** Searches reusable nutrient-bearing options saved for the current restaurant. */
    public List<NutritionFood> savedDiningOutOptions(
            String storeName,
            DiningOutIdentity identity,
            String query,
            int limit
    ) {
        return savedDiningOutComponents(storeName, identity, null, query, limit);
    }

    /**
     * Searches reusable components for the current restaurant and one composition group.
     * Group filtering happens before repository deduplication and result limiting.
     */
    public List<NutritionFood> savedDiningOutComponents(
            String storeName,
            DiningOutIdentity identity,
            String groupType,
            String query,
            int limit
    ) {
        String normalizedStoreName = storeName == null ? "" : storeName.trim();
        if (normalizedStoreName.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedStoreKey = normalizeDiningOutComponentName(normalizedStoreName);
        int safeLimit = Math.max(1, Math.min(limit, SAVED_DINING_OUT_OPTION_RESULT_LIMIT_MAX));
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedGroupType = optionalDiningOutComponentGroupType(groupType);
        List<NutritionFood> candidates = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.ownedFoodsByKindAndSource(
                userId,
                NutritionFood.KIND_EXTERNAL_MENU,
                DINING_OUT_OPTION_SOURCE_TYPE
        )) {
            if (entity.getName() != null
                    && entity.getName().toLowerCase(Locale.ROOT)
                    .contains(normalizedQuery.toLowerCase(Locale.ROOT))) {
                candidates.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                        .get(entity.getId())));
            }
        }
        List<NutritionFood> results = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (NutritionFood candidate : candidates) {
            if (!normalizedStoreKey.equals(normalizeDiningOutComponentName(candidate.brand))) {
                continue;
            }
            if (normalizedGroupType != null
                    && !normalizedGroupType.equals(optionGroupType(candidate.sourceReference))) {
                continue;
            }
            if (!matchesDiningOutOptionIdentity(candidate, identity)) {
                continue;
            }
            String normalizedName = normalizeDiningOutComponentName(candidate.name);
            String optionKey = optionGroupType(candidate.sourceReference) + "\u0000" + normalizedName;
            if (normalizedName.isEmpty() || !names.add(optionKey)) {
                continue;
            }
            results.add(candidate);
            if (results.size() >= safeLimit) {
                break;
            }
        }
        return results;
    }

    /** Source-compatible all-group component search. */
    public List<NutritionFood> savedDiningOutComponents(
            String storeName,
            DiningOutIdentity identity,
            String query,
            int limit
    ) {
        return savedDiningOutComponents(storeName, identity, null, query, limit);
    }


    /** Source-compatible ADD_ON API backed by the generic component relationship. */
    public void linkDiningOutAddOnToMenu(String menuFoodId, String addOnFoodId) {
        String menuId = requiredId(menuFoodId, "외식 메뉴");
        String componentId = requiredId(addOnFoodId, "추가 구성");
        NutritionFood component = diningOutComponent(componentId);
        if (component == null || !CompositionGroupType.ADD_ON.value().equals(
                optionGroupType(component.sourceReference))) {
            throw new IllegalArgumentException("추가 구성 그룹만 메뉴에 영구 연결할 수 있습니다.");
        }
        linkDiningOutComponentToMenu(menuId, componentId, CompositionGroupType.ADD_ON.value());
    }

    /** Links one saved component as a possible component of a saved menu. */
    public void linkDiningOutComponentToMenu(String menuFoodId, String componentFoodId) {
        String componentId = requiredId(componentFoodId, "외식 구성품");
        NutritionFood component = diningOutComponent(componentId);
        if (component == null) {
            throw new IllegalArgumentException("현재 계정의 외식 구성품만 연결할 수 있습니다.");
        }
        linkDiningOutComponentToMenu(
                menuFoodId,
                componentId,
                optionGroupType(component.sourceReference)
        );
    }

    /** Links one saved component with an explicit fixed composition group type. */
    public void linkDiningOutComponentToMenu(
            String menuFoodId,
            String componentFoodId,
            String groupType
    ) {
        String menuId = requiredId(menuFoodId, "외식 메뉴");
        String componentId = requiredId(componentFoodId, "외식 구성품");
        if (!ownedActiveFood(menuId, NutritionFood.KIND_EXTERNAL_MENU)
                || diningOutComponent(componentId) == null) {
            throw new IllegalArgumentException("현재 계정의 외식 메뉴·구성품만 연결할 수 있습니다.");
        }
        String normalizedGroupType = normalizeLinkGroupType(groupType);
        String now = OffsetDateTime.now().toString();
        nutritionDao.insertComponentLink(new DiningOutMenuComponentLinksRoomEntity(
                UUID.randomUUID().toString(),
                userId,
                menuId,
                componentId,
                normalizedGroupType,
                now,
                now,
                null,
                "android-local"
        ));
    }

    /** Returns every possible component linked to one saved menu. */
    public List<NutritionFood> diningOutComponentsForMenu(String menuFoodId) {
        return diningOutComponentsForMenu(menuFoodId, null);
    }

    /** Returns possible components for one menu and one fixed group type. */
    public List<NutritionFood> diningOutComponentsForMenu(
            String menuFoodId,
            String groupType
    ) {
        String menuId = menuFoodId == null ? "" : menuFoodId.trim();
        if (menuId.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedGroupType = groupType == null || groupType.trim().isEmpty()
                ? null
                : normalizeLinkGroupType(groupType);
        List<String> ids = nutritionDao.componentIdsForMenu(userId, menuId, normalizedGroupType);
        return readDiningOutComponentsInLinkOrder(ids);
    }

    /** Backward-compatible ADD_ON read API. */
    public List<NutritionFood> diningOutAddOnsForMenu(String menuFoodId) {
        return diningOutComponentsForMenu(menuFoodId, CompositionGroupType.ADD_ON.value());
    }

    private List<NutritionFood> readDiningOutComponentsInLinkOrder(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder placeholders = new StringBuilder();
        for (String ignored : ids) {
            placeholders.append(placeholders.length() == 0 ? "?" : ", ?");
        }
        List<String> foodArgs = new ArrayList<>();
        foodArgs.add(userId);
        foodArgs.add(NutritionFood.KIND_EXTERNAL_MENU);
        foodArgs.add(DINING_OUT_OPTION_SOURCE_TYPE);
        foodArgs.addAll(ids);
        List<NutritionFood> foods = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.visibleFoodsByIds(ids, userId)) {
            if (userId.equals(entity.getOwnerId())
                    && NutritionFood.KIND_EXTERNAL_MENU.equals(entity.getKind())
                    && DINING_OUT_OPTION_SOURCE_TYPE.equals(entity.getSourceType())) {
                foods.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                        .get(entity.getId())));
            }
        }
        Map<String, NutritionFood> byId = new LinkedHashMap<>();
        for (NutritionFood food : foods) {
            byId.put(food.id, food);
        }
        List<NutritionFood> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String id : ids) {
            if (seen.add(id) && byId.containsKey(id)) {
                ordered.add(byId.get(id));
            }
        }
        return ordered;
    }
    private boolean ownedActiveFood(String foodId, String expectedKind) {
        return nutritionDao.ownsActiveFood(foodId, userId, expectedKind) != null;
    }

    private NutritionFood diningOutComponent(String componentId) {
        NutritionFoodsRoomEntity entity = nutritionDao.ownedActiveFood(
                componentId, userId, NutritionFood.KIND_EXTERNAL_MENU
        );
        if (entity == null || !DINING_OUT_OPTION_SOURCE_TYPE.equals(entity.getSourceType())) {
            return null;
        }
        return buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId())).get(entity.getId()));
    }

    private String normalizeLinkGroupType(String groupType) {
        String normalized = groupType == null ? "" : groupType.trim();
        if ("review_event".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException(
                    "review_event는 구성 종류가 아니라 제공 방식으로 저장해야 합니다."
            );
        }
        return CompositionGroupType.normalize(normalized);
    }
    private String optionalDiningOutComponentGroupType(String groupType) {
        if (groupType == null || groupType.trim().isEmpty()) {
            return null;
        }
        return normalizeLinkGroupType(groupType);
    }


    private String optionGroupType(String sourceReference) {

        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return CompositionGroupType.OTHER.value();
        }
        try {
            JSONObject source = new JSONObject(sourceReference);
            return CompositionGroupType.normalize(source.optString(
                    "composition_group_type",
                    source.optString("composition_group_label", "")
            ));
        } catch (JSONException error) {
            return CompositionGroupType.OTHER.value();
        }
    }

    static String normalizeDiningOutComponentName(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String requiredId(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " ID가 필요합니다.");
        }
        return normalized;
    }
    /** Private dining-out menus saved by the current Nutrition owner for reuse in meal entry. */
    public List<NutritionFood> savedDiningOutMenus() {
        List<NutritionFood> candidates = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.ownedFoodsByKindAndSourceTypes(
                userId,
                NutritionFood.KIND_EXTERNAL_MENU,
                java.util.Arrays.asList(DINING_OUT_MENU_SOURCE_TYPE, OCR_DINING_OUT_MENU_SOURCE_TYPE)
        )) {
            candidates.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                    .get(entity.getId())));
        }
        Map<String, NutritionFood> canonicalMenus = new LinkedHashMap<>();
        for (NutritionFood candidate : candidates) {
            String identityKey = canonicalDiningOutMenuKey(candidate);
            if (!canonicalMenus.containsKey(identityKey)) {
                canonicalMenus.put(identityKey, candidate);
            }
        }
        return new ArrayList<>(canonicalMenus.values());
    }

    /** Canonical identity used by the saved dining-out menu list and menu upsert. */
    static String canonicalDiningOutMenuKey(
            String storeName,
            String menuName,
            String sourceReference
    ) {
        return DiningOutMenuCanonicalIdentity.from(
                storeName,
                menuName,
                sourceReference
        ).key();
    }

    static String canonicalDiningOutMenuKey(NutritionFood food) {
        if (food == null) {
            return "unresolved|menu";
        }
        return canonicalDiningOutMenuKey(food.brand, food.name, food.sourceReference);
    }
    private boolean matchesDiningOutOptionIdentity(
            NutritionFood food,
            DiningOutIdentity identity
    ) {
        if (identity == null || food == null
                || food.sourceReference == null
                || food.sourceReference.trim().isEmpty()) {
            return true;
        }
        try {
            JSONObject reference = new JSONObject(food.sourceReference);
            String restaurantId = nullableString(reference, "restaurant_id");
            String restaurantName = nullableString(reference, "restaurant_name");
            // Components are reusable at restaurant scope. Branch/location and menu IDs are
            // snapshots or catalog context, not reusable component identity.
            if (restaurantId == null && restaurantName == null) {
                return true;
            }
            if (restaurantId != null) {
                return identity.restaurantId.equals(restaurantId)
                        || (restaurantName != null
                        && normalizeDiningOutComponentName(identity.restaurantName)
                        .equals(normalizeDiningOutComponentName(restaurantName)));
            }
            return normalizeDiningOutComponentName(identity.restaurantName)
                    .equals(normalizeDiningOutComponentName(restaurantName));
        } catch (JSONException error) {
            return false;
        }
    }

    private boolean sameDiningOutRestaurantScope(
            String leftSourceReference,
            String rightSourceReference
    ) {
        try {
            JSONObject left = new JSONObject(
                    leftSourceReference == null ? "{}" : leftSourceReference
            );
            JSONObject right = new JSONObject(
                    rightSourceReference == null ? "{}" : rightSourceReference
            );
            String leftRestaurantId = nullableString(left, "restaurant_id");
            String rightRestaurantId = nullableString(right, "restaurant_id");
            String leftRestaurantName = nullableString(left, "restaurant_name");
            String rightRestaurantName = nullableString(right, "restaurant_name");
            if (leftRestaurantId != null && rightRestaurantId != null) {
                return leftRestaurantId.equals(rightRestaurantId);
            }
            if (leftRestaurantId != null || rightRestaurantId != null) {
                return leftRestaurantName != null
                        && leftRestaurantName.equalsIgnoreCase(rightRestaurantName);
            }
            return leftRestaurantName == null
                    ? rightRestaurantName == null
                    : leftRestaurantName.equalsIgnoreCase(rightRestaurantName);
        } catch (JSONException error) {
            return false;
        }
    }

    /** Saved recipes for the menu browser. */
    public List<NutritionFood> savedRecipes() {
        List<NutritionFood> recipes = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : nutritionDao.visibleFoods(userId)) {
            if (NutritionFood.KIND_RECIPE.equals(entity.getKind())) {
                recipes.add(buildFood(
                        entity,
                        loadMicronutrientsRoom(singleton(entity.getId())).get(entity.getId())
                ));
            }
        }
        return recipes;
    }

    /** Components of a saved recipe, returned in the order used when it was saved. */
    public List<RecipeComponent> recipeComponents(String recipeId) {
        if (recipeId == null || recipeId.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<NutritionFoodComponentsRoomEntity> componentRows = nutritionDao.componentsForFood(recipeId);
        List<String> foodIds = new ArrayList<>();
        for (NutritionFoodComponentsRoomEntity component : componentRows) {
            foodIds.add(component.getChildFoodId());
        }
        Map<String, NutritionFoodsRoomEntity> foodsById = new LinkedHashMap<>();
        for (NutritionFoodsRoomEntity food : nutritionDao.visibleFoodsByIds(foodIds, userId)) {
            foodsById.put(food.getId(), food);
        }
        Map<String, Map<String, Double>> micronutrients = loadMicronutrientsRoom(foodIds);
        List<RecipeComponent> components = new ArrayList<>();
        for (NutritionFoodComponentsRoomEntity component : componentRows) {
            NutritionFoodsRoomEntity entity = foodsById.get(component.getChildFoodId());
            if (entity != null) {
                NutritionFood food = buildFood(entity, micronutrients.get(entity.getId()));
                components.add(new RecipeComponent(food, component.getQuantity(), component.getUnit()));
            }
        }
        return components;
    }

    private List<NutritionFood> buildFoods(List<NutritionFoodsRoomEntity> entities) {
        List<String> ids = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : entities) {
            ids.add(entity.getId());
        }
        Map<String, Map<String, Double>> micronutrients = loadMicronutrientsRoom(ids);
        List<NutritionFood> foods = new ArrayList<>();
        for (NutritionFoodsRoomEntity entity : entities) {
            foods.add(buildFood(entity, micronutrients.get(entity.getId())));
        }
        return foods;
    }

    private Map<String, Map<String, Double>> loadMicronutrientsRoom(List<String> foodIds) {
        Map<String, Map<String, Double>> byFood = new LinkedHashMap<>();
        if (foodIds == null || foodIds.isEmpty()) {
            return byFood;
        }
        for (NutritionFoodNutrientsRoomEntity nutrient : nutritionDao.nutrientsForFoods(foodIds)) {
            String code = NutrientCode.normalize(nutrient.getNutrientCode());
            if (!NutrientCode.isKnown(code) || nutrient.getAmount() == null) {
                continue;
            }
            byFood.computeIfAbsent(nutrient.getFoodId(), key -> new LinkedHashMap<>())
                    .put(code, nutrient.getAmount());
        }
        return byFood;
    }

    private NutritionFood buildFood(
            NutritionFoodsRoomEntity row,
            Map<String, Double> micronutrients
    ) {
        NutritionProfile.Builder profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, row.getCaloriesKcal())
                .value(NutritionProfile.PROTEIN_GRAMS, row.getProteinGrams())
                .value(NutritionProfile.CARBS_GRAMS, row.getCarbsGrams())
                .value(NutritionProfile.FAT_GRAMS, row.getFatGrams());
        profile.value(NutritionProfile.SODIUM_MG, row.getSodiumMg());
        profile.value(NutritionProfile.SATURATED_FAT_GRAMS, row.getSaturatedFatGrams());
        profile.value(NutritionProfile.SUGARS_GRAMS, row.getSugarsGrams());
        profile.value(NutritionProfile.FIBER_GRAMS, row.getFiberGrams());
        profile.value(NutritionProfile.ADDED_SUGARS_GRAMS, row.getAddedSugarsGrams());
        profile.value(NutritionProfile.TRANS_FAT_GRAMS, row.getTransFatGrams());
        profile.value(NutritionProfile.CHOLESTEROL_MG, row.getCholesterolMg());
        if (micronutrients != null) {
            for (Map.Entry<String, Double> entry : micronutrients.entrySet()) {
                profile.micronutrient(entry.getKey(), entry.getValue());
            }
        }
        return NutritionFood.builder()
                .id(row.getId())
                .ownerId(row.getOwnerId())
                .name(row.getName())
                .brand(row.getBrand())
                .manufacturerName(row.getManufacturerName())
                .brandName(row.getBrandName())
                .subBrandName(row.getSubBrandName())
                .productName(row.getProductName())
                .packageAmount(row.getPackageAmount())
                .packageUnit(row.getPackageUnit())
                .packageCount(row.getPackageCount() == null ? null : row.getPackageCount().intValue())
                .kind(row.getKind())
                .category(row.getCategory())
                .basis(positiveOrDefault(row.getBasisAmount()), emptyToDefault(row.getBasisUnit(), "serving"))
                .prepState(row.getPrepState())
                .cookingMethod(row.getCookingMethod())
                .profile(profile.build())
                .source(emptyToDefault(row.getSourceType(), "manual"), row.getSourceReference())
                .sourceVersion(row.getSourceVersion())
                .dataVersion((int) row.getDataVersion())
                .revision((int) row.getRevision())
                .build();
    }

    private static List<String> singleton(String value) {
        List<String> values = new ArrayList<>();
        values.add(value);
        return values;
    }

    /**
     * 새 음식/재료를 저장한다.
     *
     * <p>필수 7종이 모두 있어야 저장된다. 권고 영양소는 비워 두면 NULL(모름)로 남고,
     * 0으로 채워지지 않는다.</p>
     */
    public NutritionFood saveFood(
            String name,
            String kind,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        return saveFood(
                name,
                null,
                kind,
                NutritionFood.categoryForKind(kind),
                basisAmount,
                basisUnit,
                NutritionFood.cookingMethodForPrepState(prepState),
                profile,
                sourceType,
                sourceReference,
                sourceVersion
        );
    }

    /** 기존 호출부와 레거시 입력을 위한 호환 오버로드. */
    public NutritionFood saveFood(
            String name,
            String brand,
            String kind,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        return saveFood(
                name,
                brand,
                kind,
                NutritionFood.categoryForKind(kind),
                basisAmount,
                basisUnit,
                NutritionFood.cookingMethodForPrepState(prepState),
                profile,
                sourceType,
                sourceReference,
                sourceVersion
        );
    }

    public NutritionFood saveFood(
            String name,
            String brand,
            String kind,
            String category,
            double basisAmount,
            String basisUnit,
            String cookingMethod,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        String normalizedName = requireName(name);
        String normalizedBrand = emptyToNull(brand);
        String normalizedKind = NutritionFood.normalizeKind(kind);
        String normalizedCategory = NutritionFood.normalizeCategory(category);
        String normalizedUnit = NutritionUnit.requireSupported(basisUnit);
        String normalizedCookingMethod = NutritionFood.normalizeCookingMethod(cookingMethod);
        String normalizedPrepState = NutritionFood.prepStateForCookingMethod(normalizedCookingMethod);
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        NutritionProfile normalizedProfile = requireRequiredNutrients(profile);

        NutritionFood food = NutritionFood.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(userId)
                .name(normalizedName)
                .brand(normalizedBrand)
                .kind(normalizedKind)
                .category(normalizedCategory)
                .basis(basisAmount, normalizedUnit)
                .prepState(normalizedPrepState)
                .cookingMethod(normalizedCookingMethod)
                .profile(normalizedProfile)
                .source(emptyToDefault(sourceType, "manual"), emptyToNull(sourceReference))
                .sourceVersion(emptyToNull(sourceVersion))
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();

        String timestamp = now();
        roomDatabase.runInTransaction(() -> {
            nutritionDao.insertFood(foodEntity(food, timestamp));
            replaceMicronutrientsRoom(food);
        });
        return food;
    }

    /**
     * Saves one Fitness nutrition row for a packaged-food package/variant.
     * PriceTrace remains the canonical product owner; an exact product, when supplied, is
     * recorded only through product_nutrition_links.
     */
    public NutritionFood savePackagedFood(
            String manufacturerName,
            String brandName,
            String subBrandName,
            String productName,
            Double packageAmount,
            String packageUnit,
            Integer packageCount,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion
    ) {
        return savePackagedFood(
                manufacturerName,
                brandName,
                subBrandName,
                productName,
                packageAmount,
                packageUnit,
                packageCount,
                basisAmount,
                basisUnit,
                prepState,
                profile,
                sourceType,
                sourceReference,
                sourceVersion,
                null
        );
    }

    /** Saves a packaged package/variant and optionally creates an exact PriceTrace link. */
    public NutritionFood savePackagedFood(
            String manufacturerName,
            String brandName,
            String subBrandName,
            String productName,
            Double packageAmount,
            String packageUnit,
            Integer packageCount,
            double basisAmount,
            String basisUnit,
            String prepState,
            NutritionProfile profile,
            String sourceType,
            String sourceReference,
            String sourceVersion,
            ProductReadV1 exactProduct
    ) {
        String normalizedProductName = emptyToNull(productName);
        String normalizedManufacturerName = emptyToNull(manufacturerName);
        String normalizedBrandName = emptyToNull(brandName);
        String normalizedSubBrandName = emptyToNull(subBrandName);
        Double resolvedPackageAmount = packageAmount;
        String resolvedPackageUnit = normalizePackagedUnit(packageUnit);
        Integer resolvedPackageCount = packageCount;
        if (exactProduct != null) {
            if (normalizedManufacturerName == null) {
                normalizedManufacturerName = exactProduct.manufacturerName;
            }
            if (normalizedBrandName == null) {
                normalizedBrandName = exactProduct.brand;
            }
            if (normalizedSubBrandName == null) {
                normalizedSubBrandName = exactProduct.subBrandName;
            }
            if (productName == null || productName.trim().isEmpty()) {
                normalizedProductName = exactProduct.name;
            }
            if (resolvedPackageAmount == null) {
                resolvedPackageAmount = exactProduct.contentAmount;
            }
            if (resolvedPackageUnit == null) {
                resolvedPackageUnit = normalizePackagedUnit(exactProduct.contentUnit);
            }
            if (resolvedPackageCount == null) {
                resolvedPackageCount = exactProduct.packageCount;
            }
        }
        normalizedProductName = requireName(normalizedProductName);
        if (resolvedPackageAmount != null && resolvedPackageAmount <= 0) {
            throw new IllegalArgumentException("포장 용량은 0보다 커야 합니다.");
        }
        if (resolvedPackageAmount != null && resolvedPackageUnit == null) {
            throw new IllegalArgumentException("포장 용량의 단위를 입력하세요.");
        }
        if (resolvedPackageCount != null && resolvedPackageCount <= 0) {
            throw new IllegalArgumentException("포장 개수는 0보다 커야 합니다.");
        }
        if (basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        String normalizedBasisUnit = NutritionUnit.requireSupported(basisUnit);
        NutritionProfile normalizedProfile = requireRequiredNutrients(profile);
        String normalizedSourceType = emptyToDefault(
                sourceType,
                exactProduct == null ? "manual" : "pricetrace_manual"
        );
        NutritionFood food = NutritionFood.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(userId)
                .name(normalizedProductName)
                .brand(normalizedBrandName)
                .manufacturerName(normalizedManufacturerName)
                .brandName(normalizedBrandName)
                .subBrandName(normalizedSubBrandName)
                .productName(normalizedProductName)
                .packageAmount(resolvedPackageAmount)
                .packageUnit(resolvedPackageUnit)
                .packageCount(resolvedPackageCount)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_PROCESSED)
                .basis(basisAmount, normalizedBasisUnit)
                .prepState(emptyToDefault(prepState, NutritionFood.PREP_UNSPECIFIED))
                .profile(normalizedProfile)
                .source(normalizedSourceType, emptyToNull(sourceReference))
                .sourceVersion(emptyToNull(sourceVersion))
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();

        String timestamp = now();
        roomDatabase.runInTransaction(() -> {
            nutritionDao.insertFood(foodEntity(food, timestamp));
            replaceMicronutrientsRoom(food);
        });
        if (exactProduct != null) {
            linkProduct(food.id, exactProduct);
        }
        return food;
    }

    /**
     * Saves a dining-out menu as a private external-menu catalog row using macro-only data.
     *
     * <p>Legacy/import/backward-compatibility API only. New UI and production writes must use
     * the explicit-calorie {@code saveDiningOutMenuWithNutrition(...)} API.</p>
     *
     * @deprecated Do not use for new UI or production writes; kcal is estimated from macros.
     */
    @Deprecated
    public NutritionFood saveDiningOutMenu(
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        return saveDiningOutMenu(
                storeName,
                menuName,
                carbsGrams,
                proteinGrams,
                fatGrams,
                null,
                null
        );
    }

    /**
     * Saves a macro-only dining-out menu and preserves a PriceTrace identity.
     *
     * <p>Legacy/import/backward-compatibility API only. New UI and production writes must use
     * the explicit-calorie {@code saveDiningOutMenuWithNutrition(...)} API.</p>
     *
     * @deprecated Do not use for new UI or production writes; kcal is estimated from macros.
     */
    @Deprecated
    public NutritionFood saveDiningOutMenu(
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            DiningOutIdentity identity
    ) {
        return saveDiningOutMenu(
                storeName,
                menuName,
                carbsGrams,
                proteinGrams,
                fatGrams,
                identity == null ? null : identity.branchName,
                identity
        );
    }

    /**
     * Saves a macro-only dining-out menu with branch and PriceTrace identity metadata.
     *
     * <p>Legacy/import/backward-compatibility API only. New UI and production writes must use
     * the explicit-calorie {@code saveDiningOutMenuWithNutrition(...)} API.</p>
     *
     * @deprecated Do not use for new UI or production writes; kcal is estimated from macros.
     */
    @Deprecated
    public NutritionFood saveDiningOutMenu(
            String storeName,
            String menuName,
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams,
            String branchName,
            DiningOutIdentity identity
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        MealEntryPolicy.requireDiningOutEstimatedMacros(carbsGrams, proteinGrams, fatGrams);
        if (!MealEntryPolicy.hasDiningOutEstimatedMacros(
                carbsGrams,
                proteinGrams,
                fatGrams
        )) {
            throw new IllegalArgumentException(
                    "메뉴로 저장하려면 추정 탄수화물·단백질·지방을 입력하세요."
            );
        }

        return saveDiningOutMenuCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                NutritionProfile.ofMacros(
                        MealEntryPolicy.estimatedDiningOutCalories(
                                carbsGrams,
                                proteinGrams,
                                fatGrams
                        ),
                        proteinGrams,
                        carbsGrams,
                        fatGrams
                ),
                NutritionFood.DATA_VERSION_MACROS_ONLY,
                diningOutMenuSourceReference(
                        normalizedStoreName,
                        normalizedMenuName,
                        branchName,
                        identity
                )
        );
    }

    /** Saves a user-estimated dining-out nutrition profile for reuse. */
    public NutritionFood saveDiningOutMenuWithNutrition(
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        return saveDiningOutMenuWithNutrition(
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                null,
                null
        );
    }

    /** Saves a menu estimate with an explicitly selected PriceTrace identity. */
    public NutritionFood saveDiningOutMenuWithNutrition(
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            DiningOutIdentity identity
    ) {
        return saveDiningOutMenuWithNutrition(
                storeName,
                menuName,
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams,
                identity == null ? null : identity.branchName,
                identity
        );
    }

    /** Saves a complete menu estimate with an explicitly selected PriceTrace identity. */
    public NutritionFood saveDiningOutMenuWithNutrition(
            String storeName,
            String menuName,
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            String branchName,
            DiningOutIdentity identity
    ) {
        MealEntryPolicy.requireDiningOutMenuNutrition(
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sodiumMg,
                sugarsGrams,
                saturatedFatGrams
        );

        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, calories.doubleValue())
                .value(NutritionProfile.PROTEIN_GRAMS, proteinGrams)
                .value(NutritionProfile.CARBS_GRAMS, carbsGrams)
                .value(NutritionProfile.FAT_GRAMS, fatGrams)
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFatGrams)
                .build();
        return saveDiningOutMenuWithNutrition(
                storeName,
                menuName,
                profile,
                branchName,
                identity
        );
    }

    /** Saves a reusable dining-out menu without dropping already-known nutrition fields. */
    public NutritionFood saveDiningOutMenuWithNutrition(
            String storeName,
            String menuName,
            NutritionProfile profile,
            String branchName,
            DiningOutIdentity identity
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        NutritionProfile sourceProfile = profile == null
                ? NutritionProfile.empty()
                : profile;
        Double carbsGrams = sourceProfile.value(NutritionProfile.CARBS_GRAMS);
        Double proteinGrams = sourceProfile.value(NutritionProfile.PROTEIN_GRAMS);
        Double fatGrams = sourceProfile.value(NutritionProfile.FAT_GRAMS);
        Double calories = sourceProfile.value(NutritionProfile.CALORIES_KCAL);
        MealEntryPolicy.requireDiningOutMenuNutrition(
                calories,
                proteinGrams,
                carbsGrams,
                fatGrams,
                sourceProfile.value(NutritionProfile.SODIUM_MG),
                sourceProfile.value(NutritionProfile.SUGARS_GRAMS),
                sourceProfile.value(NutritionProfile.SATURATED_FAT_GRAMS)
        );
        NutritionProfile normalizedProfile = NutritionProfile.builder()
                .from(sourceProfile)
                .build();
        return saveDiningOutMenuCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                normalizedProfile,
                normalizedProfile.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY,
                diningOutMenuSourceReference(
                        normalizedStoreName,
                        normalizedMenuName,
                        branchName,
                        identity
                )
        );
    }

    /** Saves one component through the source-compatible option API. */
    public NutritionFood saveDiningOutOption(
            String storeName,
            String menuName,
            DiningOutOption option
    ) {
        return saveDiningOutOption(storeName, menuName, null, option);
    }

    /** Saves one option while retaining the exact restaurant -> location -> menu identity. */
    public NutritionFood saveDiningOutOption(
            String storeName,
            String menuName,
            DiningOutIdentity identity,
            DiningOutOption option
    ) {
        if (option == null) {
            throw new IllegalArgumentException("외식 옵션이 필요합니다.");
        }
        return saveDiningOutComponent(
                storeName,
                menuName,
                identity,
                DiningOutComponent.fromOption(option)
        );
    }

    public NutritionFood saveDiningOutComponent(
            String storeName,
            String menuName,
            DiningOutComponent component
    ) {
        return saveDiningOutComponent(storeName, menuName, null, component);
    }

    /** Saves a reusable component without storing its actual-meal provision type in the link source. */
    public NutritionFood saveDiningOutComponent(
            String storeName,
            String menuName,
            DiningOutIdentity identity,
            DiningOutComponent component
    ) {
        String normalizedStoreName = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedMenuName = MealEntryPolicy.requireDiningOutMenuName(menuName);
        if (component == null) {
            throw new IllegalArgumentException("외식 구성품이 필요합니다.");
        }
        NutritionProfile profile = component.profile;
        if (!component.hasNutrition()) {
            return saveDiningOutOptionCatalogRow(
                    normalizedStoreName,
                    normalizedMenuName,
                    component.name,
                    profile,
                    diningOutOptionSourceReference(
                            normalizedStoreName,
                            normalizedMenuName,
                            identity,
                            component
                    )
            );
        }
        // A component profile may be partial or entirely unknown. Never infer a missing value
        // from the component name or from other macros; NULL and numeric zero are distinct.
        return saveDiningOutOptionCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                component.name,
                profile,
                diningOutOptionSourceReference(
                        normalizedStoreName,
                        normalizedMenuName,
                        identity,
                        component
                )
        );
    }

    private String diningOutOptionSourceReference(
            String storeName,
            String menuName,
            DiningOutIdentity identity,
            DiningOutOption option
    ) {
        JSONObject reference = new JSONObject();
        try {
            reference.put("contract_version", "dining-out-component.v1");
            reference.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
            reference.put("restaurant_name", identity == null
                    ? storeName
                    : identity.restaurantName);
            reference.put("composition_group_key", option.groupKey);
            reference.put("composition_group_type", option.groupType);
            reference.put("composition_group_label", option.groupLabel);
            reference.put("composition_role", option.role);
            if (option.sourceReference != null && !option.sourceReference.trim().isEmpty()) {
                try {
                    JSONObject previous = new JSONObject(option.sourceReference);
                    if (previous.has("composition_template_id")) {
                        reference.put(
                                "composition_template_id",
                                previous.optString("composition_template_id", "")
                        );
                    }
                    if (previous.has("composition_template_revision")) {
                        reference.put(
                                "composition_template_revision",
                                previous.isNull("composition_template_revision")
                                        ? JSONObject.NULL
                                        : previous.optInt("composition_template_revision", 1)
                        );
                    }
                } catch (JSONException ignored) {
                    // Preserve the normal catalog identity when an older source is not JSON.
                }
            }
            if (option.memberId == null) {
                reference.put("composition_member_id", JSONObject.NULL);
            } else {
                reference.put("composition_member_id", option.memberId);
            }
            if (identity == null) {
                reference.put("namespace", "fitnessapp");
                reference.put("restaurant_id", JSONObject.NULL);
                reference.put("restaurant_location_id", JSONObject.NULL);
                reference.put("source_location_code", JSONObject.NULL);
                reference.put("branch_name", JSONObject.NULL);
                reference.put("restaurant_menu_id", JSONObject.NULL);
                reference.put("menu_name", menuName);
                reference.put("catalog_product_id", JSONObject.NULL);
            } else {
                reference.put("namespace", DiningOutIdentity.NAMESPACE);
                reference.put("source_namespace", identity.locationSourceNamespace == null
                        ? JSONObject.NULL : identity.locationSourceNamespace);
                reference.put("restaurant_id", identity.restaurantId);
                reference.put("restaurant_location_id", identity.restaurantLocationId);
                reference.put("source_location_code", identity.sourceLocationCode == null
                        ? JSONObject.NULL : identity.sourceLocationCode);
                reference.put("branch_name", identity.branchName == null
                        ? JSONObject.NULL : identity.branchName);
                reference.put("restaurant_menu_id", identity.restaurantMenuId);
                reference.put("menu_name", identity.menuName);
                reference.put("catalog_product_id", identity.catalogProductId);
            }
            return reference.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Dining-out option identity could not be encoded.", error);
        }
    }

    private String diningOutMenuSourceReference(
            String restaurantName,
            String menuName,
            String branchName,
            DiningOutIdentity identity
    ) {
        if (identity != null) {
            return identity.metadataJson();
        }
        JSONObject reference = new JSONObject();
        try {
            reference.put("schema_version", "dining-out-identity.v1");
            reference.put("namespace", "fitnessapp");
            reference.put("restaurant_id", JSONObject.NULL);
            reference.put("restaurant_name", restaurantName);
            reference.put("restaurant_location_id", JSONObject.NULL);
            if (branchName == null || branchName.trim().isEmpty()) {
                reference.put("branch_name", JSONObject.NULL);
            } else {
                reference.put("branch_name", branchName.trim());
            }
            reference.put("restaurant_menu_id", JSONObject.NULL);
            reference.put("menu_name", menuName);
            reference.put("catalog_product_id", JSONObject.NULL);
            return reference.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Dining-out publication identity could not be encoded.", error);
        }
    }

    private NutritionFood saveDiningOutMenuCatalogRow(
            String normalizedStoreName,
            String normalizedMenuName,
            NutritionProfile profile,
            int dataVersion,
            String sourceReference
    ) {
        return saveDiningOutCatalogRow(
                normalizedStoreName,
                normalizedMenuName,
                normalizedMenuName,
                profile,
                dataVersion,
                "manual_estimate",
                sourceReference
        );
    }

    private NutritionFood saveDiningOutOptionCatalogRow(
            String normalizedStoreName,
            String normalizedMenuName,
            String normalizedOptionName,
            NutritionProfile profile,
            String sourceReference
    ) {
        return saveDiningOutCatalogRow(
                normalizedStoreName,
                normalizedOptionName,
                normalizedMenuName,
                profile,
                profile.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY,
                DINING_OUT_OPTION_SOURCE_TYPE,
                sourceReference
        );
    }

    private NutritionFood saveDiningOutCatalogRow(
            String normalizedStoreName,
            String normalizedFoodName,
            String normalizedMenuName,
            NutritionProfile profile,
            int dataVersion,
            String sourceType,
            String sourceReference
    ) {

        String existingId = null;
        String existingCreatedAt = null;
        if (isDiningOutMenuSourceType(sourceType)) {
            ExistingCatalogRow existing = findCanonicalDiningOutMenu(
                    normalizedStoreName,
                    normalizedMenuName,
                    sourceReference
            );
            if (existing != null) {
                existingId = existing.id;
                existingCreatedAt = existing.createdAt;
            }
        } else {
            List<NutritionFood> candidates = new ArrayList<>();
            for (NutritionFoodsRoomEntity entity : nutritionDao.ownedFoodsByKindAndSource(
                    userId,
                    NutritionFood.KIND_EXTERNAL_MENU,
                    sourceType
            )) {
                candidates.add(buildFood(entity, loadMicronutrientsRoom(singleton(entity.getId()))
                        .get(entity.getId())));
            }
            String requestedGroup = optionGroupType(sourceReference);
            for (NutritionFood candidate : candidates) {
                if (!normalizeDiningOutComponentName(normalizedStoreName)
                        .equals(normalizeDiningOutComponentName(candidate.brand))
                        || !requestedGroup.equals(optionGroupType(candidate.sourceReference))
                        || !normalizeDiningOutComponentName(normalizedFoodName)
                        .equals(normalizeDiningOutComponentName(candidate.name))
                        || !sameDiningOutRestaurantScope(
                        sourceReference,
                        candidate.sourceReference
                )) {
                    continue;
                }
                existingId = candidate.id;
                break;
            }
        }

        String timestamp = now();
        NutritionFood food = NutritionFood.builder()
                .id(existingId == null ? UUID.randomUUID().toString() : existingId)
                .ownerId(userId)
                .name(normalizedFoodName)
                .brand(normalizedStoreName)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1.0, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source(sourceType, sourceReference)
                .dataVersion(dataVersion)
                .build();

        String createdAt = existingCreatedAt == null || existingCreatedAt.trim().isEmpty()
                ? timestamp : existingCreatedAt;
        NutritionFoodsRoomEntity entity = foodEntity(food, createdAt, timestamp);
        boolean insertNew = existingId == null;
        roomDatabase.runInTransaction(() -> {
            if (insertNew) {
                nutritionDao.insertFood(entity);
            } else {
                nutritionDao.upsertFood(entity);
            }
            replaceMicronutrientsRoom(food);
        });
        return food;
    }

    private ExistingCatalogRow findCanonicalDiningOutMenu(
            String storeName,
            String menuName,
            String sourceReference
    ) {
        DiningOutMenuCanonicalIdentity requested = DiningOutMenuCanonicalIdentity.from(
                storeName,
                menuName,
                sourceReference
        );
        for (NutritionFoodsRoomEntity entity : nutritionDao.ownedFoodsByKindAndSourceTypes(
                userId,
                NutritionFood.KIND_EXTERNAL_MENU,
                java.util.Arrays.asList(DINING_OUT_MENU_SOURCE_TYPE, OCR_DINING_OUT_MENU_SOURCE_TYPE)
        )) {
                DiningOutMenuCanonicalIdentity candidate = DiningOutMenuCanonicalIdentity.from(
                        entity.getBrand(),
                        entity.getName(),
                        entity.getSourceReference()
                );
                if (requested.matches(candidate)) {
                    return new ExistingCatalogRow(
                            entity.getId(),
                            entity.getCreatedAt()
                    );
                }
        }
        return null;
    }

    private static boolean isDiningOutMenuSourceType(String sourceType) {
        return DINING_OUT_MENU_SOURCE_TYPE.equalsIgnoreCase(sourceType)
                || OCR_DINING_OUT_MENU_SOURCE_TYPE.equalsIgnoreCase(sourceType);
    }

    /**
     * 구성 재료로 레시피를 만든다.
     *
     * <p>레시피의 영양성분은 개별 음식과 같은 규칙으로 합산된다. 재료 중 하나라도 모르는
     * 영양소는 레시피에서도 모름으로 남으며, 0으로 합산되지 않는다.</p>
     */
    /** Builds a composed menu for one meal without adding it to the reusable catalog. */
    public NutritionFood buildRecipeForMeal(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        return recipeFood(null, normalizedName, items);
    }

    public NutritionFood saveRecipe(String name, List<MealCompositionItem> items) {
        String normalizedName = requireName(name);
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Recipe needs at least one food.");
        }

        NutritionFood recipe = recipeFood(UUID.randomUUID().toString(), normalizedName, items);

        String timestamp = now();
        roomDatabase.runInTransaction(() -> {
            nutritionDao.insertFood(foodEntity(recipe, timestamp));
            replaceMicronutrientsRoom(recipe);
            int orderIndex = 0;
            for (MealCompositionItem item : items) {
                nutritionDao.insertComponent(new NutritionFoodComponentsRoomEntity(
                        UUID.randomUUID().toString(),
                        userId,
                        recipe.id,
                        item.food.id,
                        item.quantity,
                        item.food.basisUnit,
                        orderIndex++,
                        timestamp,
                        timestamp,
                        null
                ));
            }
        });
        return recipe;
    }

    private NutritionFood recipeFood(
            String id,
            String normalizedName,
            List<MealCompositionItem> items
    ) {

        NutritionProfile total = NutritionCalculator.recipeProfile(items);
        return NutritionFood.builder()
                .id(id)
                .ownerId(userId)
                .name(normalizedName)
                .kind(NutritionFood.KIND_RECIPE)
                .category(NutritionFood.CATEGORY_RECIPE)
                .basis(1.0, "serving")
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(total)
                .source("manual_recipe", null)
                .dataVersion(total.hasAllRequired()
                        ? NutritionFood.DATA_VERSION_REQUIRED_SEVEN
                        : NutritionFood.DATA_VERSION_MACROS_ONLY)
                .build();
    }

    /** Active user-approved PriceTrace link, enriched only from the local read cache. */
    public ProductNutritionLink approvedProductLink(String nutritionFoodId) {
        List<ProductNutritionLink> links = readProductLinks(
                nutritionFoodId,
                ProductNutritionLink.STATUS_APPROVED
        );
        return links.isEmpty() ? null : links.get(0);
    }

    /** Public means explicitly published for the approved PriceTrace product link. */
    public boolean isFoodPublic(String nutritionFoodId) {
        if (nutritionFoodId == null || nutritionFoodId.trim().isEmpty()) {
            return false;
        }
        NutritionFoodsRoomEntity food = nutritionDao.visibleFood(nutritionFoodId, userId);
        return food != null && "public".equals(food.getVisibility());
    }

    /**
     * Publishes or unpublishes only through the authenticated, validated Nutrition RPC.
     * The local visibility is updated from the authoritative RPC response.
     */
    public PublicationState setProductNutritionPublication(
            String nutritionFoodId,
            String catalogProductId,
            boolean publish
    ) throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
        String normalizedFoodId = requireName(nutritionFoodId);
        String normalizedCatalogProductId;
        try {
            normalizedCatalogProductId = UUID.fromString(catalogProductId).toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("PriceTrace 정확 규격 ID가 올바르지 않습니다.", error);
        }

        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/set_product_nutrition_publication_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", normalizedFoodId);
        request.put("p_catalog_product_id", normalizedCatalogProductId);
        request.put("p_publish", publish);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException("영양 공개 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String returnedCatalogProductId = nullableString(row, "catalog_product_id");
        String visibility = nullableString(row, "visibility");
        if (!normalizedFoodId.equals(returnedFoodId)
                || !normalizedCatalogProductId.equals(returnedCatalogProductId)
                || !("public".equals(visibility) || "private".equals(visibility))
                || publish != "public".equals(visibility)) {
            throw new IOException("영양 공개 RPC 응답이 요청한 항목과 일치하지 않습니다.");
        }

        String updatedAt = emptyToDefault(nullableString(row, "updated_at"), now());
        int changed = nutritionDao.updateVisibility(
                normalizedFoodId,
                config.effectiveUserId(),
                visibility,
                updatedAt
        );
        if (changed != 1) {
            throw new IOException("공개된 영양정보를 기기 카탈로그에 반영하지 못했습니다.");
        }
        return new PublicationState(
                normalizedFoodId,
                normalizedCatalogProductId,
                "public".equals(visibility),
                Math.max(1, row.optInt("publication_revision", 1)),
                nullableString(row, "published_at")
        );
    }

    /** Publishes a dining-out menu through the exact restaurant/menu identity contract. */
    public PublicationState setDiningOutMenuPublication(
            String nutritionFoodId,
            boolean publish
    ) throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
        String normalizedFoodId = requireName(nutritionFoodId);
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/set_dining_out_menu_publication_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", normalizedFoodId);
        request.put("p_publish", publish);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }

        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException("식당 메뉴 공개 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String visibility = nullableString(row, "visibility");
        if (!normalizedFoodId.equals(returnedFoodId)
                || !("public".equals(visibility) || "private".equals(visibility))
                || publish != "public".equals(visibility)) {
            throw new IOException("식당 메뉴 공개 RPC 응답이 요청한 항목과 일치하지 않습니다.");
        }

        String updatedAt = emptyToDefault(nullableString(row, "updated_at"), now());
        int changed = nutritionDao.updateVisibility(
                normalizedFoodId,
                config.effectiveUserId(),
                visibility,
                updatedAt
        );
        if (changed != 1) {
            throw new IOException("식당 메뉴 공개 상태를 기기 카탈로그에 반영하지 못했습니다.");
        }
        return new PublicationState(
                normalizedFoodId,
                nullableString(row, "catalog_product_id"),
                "public".equals(visibility),
                Math.max(1, row.optInt("publication_revision", 1)),
                nullableString(row, "published_at")
        );
    }

    /** Registers the FT-owned menu in PT first, then publishes its Nutrition projection. */
    public PublicationState publishDiningOutMenuToPriceTrace(
            String nutritionFoodId,
            boolean publish,
            SupabaseConfig priceTraceConfig
    ) throws Exception {
        if (!publish) {
            return setDiningOutMenuPublication(nutritionFoodId, false);
        }
        SupabaseConfig nutritionConfig = supabaseConfig;
        if (nutritionConfig == null || !nutritionConfig.isConfigured()) {
            throw new IllegalStateException("영양 DB 계정 로그인이 필요합니다.");
        }
        if (priceTraceConfig == null || !priceTraceConfig.isConfigured()) {
            throw new IllegalStateException("PT 관리자 계정 로그인이 필요합니다.");
        }

        String normalizedFoodId = requireName(nutritionFoodId);
        NutritionFood food = findFoodById(normalizedFoodId);
        if (food == null || !food.isDiningOutMenu()) {
            throw new IllegalArgumentException("공개할 FT 식당 메뉴를 찾을 수 없습니다.");
        }

        JSONObject identity = new JSONObject();
        if (food.sourceReference != null && !food.sourceReference.trim().isEmpty()) {
            try {
                identity = new JSONObject(food.sourceReference);
            } catch (JSONException ignored) {
                // Legacy "dining_out" rows use the new FitnessApp source identity below.
            }
        }

        String restaurantName = emptyToDefault(
                nullableString(identity, "restaurant_name"),
                emptyToDefault(food.brand, "식당명 미기록")
        );
        String menuName = emptyToDefault(nullableString(identity, "menu_name"), food.name);
        String restaurantId = nullableString(identity, "restaurant_id");
        String locationId = nullableString(identity, "restaurant_location_id");
        String menuId = nullableString(identity, "restaurant_menu_id");
        String catalogProductId = nullableString(identity, "catalog_product_id");
        boolean hasAnyPriceTraceId = restaurantId != null
                || locationId != null
                || menuId != null
                || catalogProductId != null;
        boolean hasAllPriceTraceIds = restaurantId != null
                && locationId != null
                && menuId != null
                && catalogProductId != null;
        if (hasAnyPriceTraceId && !hasAllPriceTraceIds) {
            throw new IllegalStateException("PT 식당·지점·메뉴 identity가 일부만 저장되어 있습니다.");
        }

        String locationSourceNamespace = nullableString(identity, "source_namespace");
        if (locationSourceNamespace == null) {
            locationSourceNamespace = nullableString(identity, "location_source_namespace");
        }
        if (locationSourceNamespace == null) {
            String legacyNamespace = nullableString(identity, "namespace");
            locationSourceNamespace = DiningOutIdentity.NAMESPACE.equals(legacyNamespace)
                    ? null : legacyNamespace;
        }
        String sourceLocationCode = nullableString(identity, "source_location_code");
        if (hasAllPriceTraceIds) {
            SourceLocationIdentity sourceLocation = resolvePriceTraceLocation(
                    priceTraceConfig,
                    restaurantId,
                    locationId,
                    locationSourceNamespace,
                    sourceLocationCode
            );
            locationSourceNamespace = emptyToDefault(
                    sourceLocation.locationSourceNamespace,
                    DiningOutIdentity.NAMESPACE
            );
            sourceLocationCode = sourceLocation.code;
            if (sourceLocationCode == null) {
                throw new IllegalStateException(
                        "PriceTrace 지점 응답에 source identity가 없습니다. PT 지점 설정을 확인하세요."
                );
            }
        } else {
            locationSourceNamespace = "fitnessapp";
            sourceLocationCode = "restaurant:" + restaurantName.trim().toLowerCase(Locale.US);
        }

        JSONObject request = new JSONObject();
        request.put("p_idempotency_key", "fitnessapp:dining-out:" + normalizedFoodId);
        request.put("p_nutrition_food_id", normalizedFoodId);
        request.put("p_nutrition_revision", Math.max(1, food.revision));
        putNullable(request, "p_restaurant_id", restaurantId);
        request.put("p_restaurant_name", restaurantName);
        putNullable(request, "p_restaurant_location_id", locationId);
        request.put("p_source_location_namespace", locationSourceNamespace);
        request.put("p_source_location_code", sourceLocationCode);
        putNullable(request, "p_location_label", nullableString(identity, "branch_name"));
        putNullable(request, "p_restaurant_menu_id", menuId);
        putNullable(request, "p_catalog_product_id", catalogProductId);
        request.put("p_menu_name", menuName);
        request.put("p_menu_category_label", NutritionFood.categoryLabel(food.category));
        request.put("p_serving_label", food.basisLabel());

        HttpURLConnection ptConnection = openConnection(
                joinUrl(priceTraceConfig.supabaseUrl,
                        "/rest/v1/rpc/admin_publish_fitness_dining_out_v1"),
                "POST",
                priceTraceConfig
        );
        ptConnection.setRequestProperty("Content-Type", "application/json");
        ptConnection.setRequestProperty("Accept", "application/json");
        ptConnection.setDoOutput(true);
        try (OutputStream output = ptConnection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        String ptBody = readResponseOrThrow(ptConnection, 200);
        JSONArray ptRows = ptBody.isEmpty() ? new JSONArray() : new JSONArray(ptBody);
        if (ptRows.length() != 1) {
            throw new IOException("PT 식당 메뉴 등록 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject ptRow = ptRows.getJSONObject(0);
        String registeredRestaurantId = requireReturnedUuid(ptRow, "restaurant_id");
        String registeredLocationId = requireReturnedUuid(ptRow, "restaurant_location_id");
        String registeredMenuId = requireReturnedUuid(ptRow, "restaurant_menu_id");
        String registeredCatalogProductId = requireReturnedUuid(ptRow, "catalog_product_id");

        attachDiningOutMenuIdentity(
                nutritionConfig,
                normalizedFoodId,
                registeredRestaurantId,
                registeredLocationId,
                registeredMenuId,
                registeredCatalogProductId
        );
        attachDiningOutMenuNutritionLink(
                nutritionConfig,
                normalizedFoodId,
                registeredCatalogProductId
        );
        return setDiningOutMenuPublication(normalizedFoodId, true);
    }

    private SourceLocationIdentity resolvePriceTraceLocation(
            SupabaseConfig priceTraceConfig,
            String restaurantId,
            String locationId,
            String storedLocationSourceNamespace,
            String storedSourceLocationCode
    ) throws Exception {
        if (storedSourceLocationCode != null && !storedSourceLocationCode.trim().isEmpty()) {
            return new SourceLocationIdentity(
                    storedLocationSourceNamespace,
                    storedSourceLocationCode.trim()
            );
        }

        RestaurantMenuReadV1Client.RestaurantDetail detail =
                new RestaurantMenuReadV1Client(priceTraceConfig).loadRestaurant(restaurantId);
        for (RestaurantMenuReadV1Client.RestaurantLocation location : detail.locations) {
            if (locationId.equals(location.restaurantLocationId)) {
                String sourceLocationCode = location.sourceLocationCode == null
                        ? ""
                        : location.sourceLocationCode.trim();
                return new SourceLocationIdentity(
                        location.locationSourceNamespace,
                        sourceLocationCode.isEmpty() ? null : sourceLocationCode
                );
            }
        }
        throw new IOException("PriceTrace 응답에서 요청한 지점 identity를 찾지 못했습니다.");
    }

    private static final class SourceLocationIdentity {
        private final String locationSourceNamespace;
        private final String code;

        private SourceLocationIdentity(String locationSourceNamespace, String code) {
            this.locationSourceNamespace = locationSourceNamespace;
            this.code = code;
        }
    }

    private void attachDiningOutMenuIdentity(
            SupabaseConfig config,
            String nutritionFoodId,
            String restaurantId,
            String locationId,
            String menuId,
            String catalogProductId
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/attach_dining_out_menu_identity_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", nutritionFoodId);
        request.put("p_restaurant_id", restaurantId);
        request.put("p_restaurant_location_id", locationId);
        request.put("p_restaurant_menu_id", menuId);
        request.put("p_catalog_product_id", catalogProductId);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException("FT 식당 메뉴 identity 연결 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String sourceReference = nullableString(row, "source_reference");
        if (!nutritionFoodId.equals(returnedFoodId) || sourceReference == null) {
            throw new IOException("FT 식당 메뉴 identity 연결 응답이 요청과 일치하지 않습니다.");
        }
        ContentValues values = new ContentValues();
        values.put("source_reference", sourceReference);
        values.put("updated_at", emptyToDefault(nullableString(row, "updated_at"), now()));
        int changed = database.update(
                "nutrition_foods",
                values,
                "id = ? AND owner_id = ? AND deleted_at IS NULL",
                new String[]{nutritionFoodId, config.effectiveUserId()}
        );
        if (changed != 1) {
            throw new IOException("FT 식당 메뉴 identity를 기기 카탈로그에 반영하지 못했습니다.");
        }
    }

    private void attachDiningOutMenuNutritionLink(
            SupabaseConfig config,
            String nutritionFoodId,
            String catalogProductId
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl,
                        "/rest/v1/rpc/attach_dining_out_menu_nutrition_link_v1"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        JSONObject request = new JSONObject();
        request.put("p_nutrition_food_id", nutritionFoodId);
        request.put("p_catalog_product_id", catalogProductId);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        JSONArray rows = body.isEmpty() ? new JSONArray() : new JSONArray(body);
        if (rows.length() != 1) {
            throw new IOException("FT 식당 메뉴 영양 링크 RPC가 정확히 한 행을 반환하지 않았습니다.");
        }
        JSONObject row = rows.getJSONObject(0);
        String returnedFoodId = nullableString(row, "nutrition_food_id");
        String returnedCatalogProductId = nullableString(row, "catalog_product_id");
        if (!nutritionFoodId.equals(returnedFoodId)
                || !catalogProductId.equals(returnedCatalogProductId)
                || !"approved".equals(nullableString(row, "status"))) {
            throw new IOException("FT 식당 메뉴 영양 링크 응답이 요청한 exact identity와 일치하지 않습니다.");
        }
    }

    /** Pending owner-specific suggestions written by a trusted PriceTrace integration. */
    public List<ProductNutritionLink> pendingProductLinkSuggestions(String nutritionFoodId) {
        return readProductLinks(nutritionFoodId, ProductNutritionLink.STATUS_SUGGESTED);
    }

    public void cachePriceTraceProducts(List<ProductReadV1> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        String fetchedAt = now();
        roomDatabase.runInTransaction(() -> {
            for (ProductReadV1 product : products) {
                if (product != null) {
                    for (ProductReadV1 exactVariant : product.exactCatalogVariants()) {
                        cachePriceTraceProductRoom(exactVariant, fetchedAt);
                    }
                }
            }
        });
    }

    /**
     * Creates an immediately approved manual link only after the caller selected an exact ID.
     * No name-based match is accepted here.
     */
    public ProductNutritionLink linkProduct(String nutritionFoodId, ProductReadV1 product) {
        if (product == null) {
            throw new IllegalArgumentException("연결할 표준상품을 선택하세요.");
        }
        if (!product.isExactCatalogProduct()) {
            throw new IllegalArgumentException(
                    "표준상품의 여러 규격 중 하나를 임의로 연결할 수 없습니다."
            );
        }
        requirePriceTraceCatalogMetadata(product);
        requireLinkableFood(nutritionFoodId);
        String timestamp = now();
        String id = UUID.randomUUID().toString();
        roomDatabase.runInTransaction(() -> {
            cachePriceTraceProductRoom(product, timestamp);
            nutritionDao.softDeleteApprovedLinks(userId, nutritionFoodId, null, timestamp);
            nutritionDao.insertProductLink(new ProductNutritionLinksRoomEntity(
                    id,
                    userId,
                    nutritionFoodId,
                    product.catalogProductId,
                    product.standardProductId,
                    ProductNutritionLink.STATUS_APPROVED,
                    ProductNutritionLink.SOURCE_MANUAL,
                    null,
                    ProductReadV1.CONTRACT_VERSION,
                    product.revision,
                    product.contentAmount,
                    product.contentUnit,
                    product.packageCount == null ? null : product.packageCount.longValue(),
                    1L,
                    timestamp,
                    timestamp,
                    timestamp,
                    null
            ));
        });
        return approvedProductLink(nutritionFoodId);
    }

    /** Approves only the catalogProductId carried by the selected suggestion row. */
    public ProductNutritionLink approveProductSuggestion(
            String suggestionId,
            ProductReadV1 exactProduct
    ) {
        if (exactProduct == null) {
            throw new IllegalArgumentException("제안된 catalogProductId를 확인할 수 없습니다.");
        }
        requirePriceTraceCatalogMetadata(exactProduct);
        ProductNutritionLinksRoomEntity suggestion = nutritionDao.suggestedLink(suggestionId, userId);
        if (suggestion == null) {
            throw new IllegalArgumentException("승인할 PriceTrace 제안을 찾지 못했습니다.");
        }
        String nutritionFoodId = suggestion.getNutritionFoodId();
        String suggestedCatalogProductId = suggestion.getCatalogProductId();
        if (!exactProduct.catalogProductId.equals(suggestedCatalogProductId)) {
            throw new IllegalArgumentException("제안 ID와 선택한 catalogProductId가 다릅니다.");
        }

        String timestamp = now();
        roomDatabase.runInTransaction(() -> {
            cachePriceTraceProductRoom(exactProduct, timestamp);
            nutritionDao.softDeleteApprovedLinks(userId, nutritionFoodId, suggestionId, timestamp);
            nutritionDao.approveSuggestedLink(
                    suggestionId,
                    userId,
                    timestamp,
                    exactProduct.revision,
                    exactProduct.contentAmount,
                    exactProduct.contentUnit,
                    exactProduct.packageCount == null ? null : exactProduct.packageCount.longValue(),
                    timestamp
            );
        });
        return approvedProductLink(nutritionFoodId);
    }

    public boolean rejectProductSuggestion(String suggestionId) {
        String timestamp = now();
        return nutritionDao.rejectSuggestedLink(suggestionId, userId, timestamp, timestamp) > 0;
    }

    /** Soft-unlinks without deleting either the Nutrition entry or any meal snapshot. */
    public boolean unlinkProduct(String nutritionFoodId) {
        ProductNutritionLink existing = approvedProductLink(nutritionFoodId);
        if (existing == null) {
            return false;
        }
        String timestamp = now();
        return nutritionDao.unlinkApprovedProduct(userId, nutritionFoodId, timestamp) > 0;
    }

    private List<ProductNutritionLink> readProductLinks(String nutritionFoodId, String status) {
        List<ProductNutritionLink> links = new ArrayList<>();
        for (NutritionRoomDao.ProductLinkRow row : nutritionDao.productLinks(
                userId, nutritionFoodId, status
        )) {
            ProductReadV1 product = null;
            if (row.getProductName() != null) {
                try {
                    product = new ProductReadV1(
                            row.getCatalogProductId(),
                            row.getStandardProductId() == null
                                    ? row.getCacheStandardProductId()
                                    : row.getStandardProductId(),
                            row.getProductName(),
                            row.getBrandName(),
                            row.getManufacturerName(),
                            row.getSubBrandName(),
                            row.getSellerName(),
                            row.getLatestPriceKrw() == null ? null : row.getLatestPriceKrw().intValue(),
                            row.getPriceObservedAt(),
                            row.getContentAmount(),
                            row.getContentUnit(),
                            row.getPackageCount() == null ? null : row.getPackageCount().intValue(),
                            row.getCacheCatalogProductRevision()
                    );
                } catch (IllegalArgumentException ignored) {
                    // A corrupt cache must not hide the underlying exact link decision.
                }
            }
            links.add(new ProductNutritionLink(
                    row.getId(),
                    row.getOwnerId(),
                    row.getNutritionFoodId(),
                    row.getCatalogProductId(),
                    row.getStandardProductId(),
                    row.getStatus(),
                    row.getSourceType(),
                    row.getProposalReference(),
                    (int) row.getRevision(),
                    row.getReviewedAt(),
                    row.getCatalogProductRevision(),
                    row.getCatalogContentAmount(),
                    row.getCatalogContentUnit(),
                    row.getCatalogPackageCount() == null ? null : row.getCatalogPackageCount().intValue(),
                    product
            ));
        }
        return links;
    }

    private void requireLinkableFood(String nutritionFoodId) {
        String normalized = nutritionFoodId == null ? "" : nutritionFoodId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("영양 음식 ID가 필요합니다.");
        }
        if (nutritionDao.visibleFood(normalized, userId) == null) {
            throw new IllegalArgumentException("연결할 영양 음식을 찾지 못했습니다.");
        }
    }

    private void softDeleteApprovedLinks(
            FitnessDatabaseConnection database,
            String nutritionFoodId,
            String exceptId,
            String timestamp
    ) {
        String exceptClause = exceptId == null ? "" : " AND id <> ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(timestamp);
        arguments.add(timestamp);
        arguments.add(userId);
        arguments.add(nutritionFoodId);
        if (exceptId != null) {
            arguments.add(exceptId);
        }
        database.execSQL(
                "UPDATE product_nutrition_links SET deleted_at = ?, updated_at = ?, " +
                        "revision = revision + 1 WHERE owner_id = ? AND nutrition_food_id = ? " +
                        "AND status = 'approved' AND deleted_at IS NULL" + exceptClause,
                arguments.toArray()
        );
    }

    private void cachePriceTraceProduct(
            FitnessDatabaseConnection database,
            ProductReadV1 product,
            String fetchedAt
    ) {
        ContentValues values = new ContentValues();
        values.put("catalog_product_id", product.catalogProductId);
        putNullable(values, "standard_product_id", product.standardProductId);
        values.put("product_name", product.name);
        putNullable(values, "brand_name", product.brand);
        putNullable(values, "manufacturer_name", product.manufacturerName);
        putNullable(values, "sub_brand_name", product.subBrandName);
        putNullable(values, "seller_name", product.sellerName);
        if (product.latestObservedPriceKrw == null) {
            values.putNull("latest_price_krw");
            values.putNull("price_observed_at");
        } else {
            values.put("latest_price_krw", product.latestObservedPriceKrw);
            values.put("price_observed_at", product.observedAt);
        }
        putNullableDouble(values, "content_amount", product.contentAmount);
        putNullable(values, "content_unit", product.contentUnit);
        if (product.packageCount == null) {
            values.putNull("package_count");
        } else {
            values.put("package_count", product.packageCount);
        }
        putNullable(values, "catalog_product_revision", product.revision);
        values.put("contract_version", ProductReadV1.CONTRACT_VERSION);
        values.put("fetched_at", fetchedAt);
        database.insertWithOnConflict(
                "pricetrace_product_cache",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private void requirePriceTraceCatalogMetadata(ProductReadV1 product) {
        if (!product.hasValidPriceTraceCatalogMetadata()) {
            throw new IllegalArgumentException(
                    "PriceTrace product-read.v1의 상품별 revision·규격값이 없거나 허용값이 아닙니다."
            );
        }
    }

    private void putPriceTraceCatalogMetadata(ContentValues values, ProductReadV1 product) {
        values.put("catalog_product_revision", product.revision);
        values.put("catalog_content_amount", product.contentAmount);
        values.put("catalog_content_unit", product.contentUnit);
        values.put("catalog_package_count", product.packageCount);
    }

    public synchronized CatalogSyncResult syncRemote() throws Exception {
        SupabaseConfig config = supabaseConfig;
        if (config == null || !config.isConnectionConfigured()) {
            return new CatalogSyncResult(0, 0);
        }

        int pulledRows = pullFoods(config);
        pulledRows += pullNutrients(config);
        pulledRows += pullComponents(config);
        if (config.isConfigured()) {
            pulledRows += pullProductLinks(config);
        }

        int pushedRows = 0;
        if (config.isConfigured()) {
            pushedRows += pushTable(config, "nutrition_foods", FOOD_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_nutrients", NUTRIENT_SYNC_COLUMNS);
            pushedRows += pushTable(config, "nutrition_food_components", COMPONENT_SYNC_COLUMNS);
            pushedRows += pushProductLinks(config);

            // Conditional writes can lose a race without overwriting it. Pull once more to
            // converge on the remote winner and to receive deletion tombstones.
            pulledRows += pullFoods(config);
            pulledRows += pullNutrients(config);
            pulledRows += pullComponents(config);
            pulledRows += pullProductLinks(config);
        }
        return new CatalogSyncResult(pushedRows, pulledRows);
    }

    private int pushTable(SupabaseConfig config, String table, String[] columns) throws Exception {
        if (!CATALOG_TABLES.contains(table)) {
            throw new IllegalArgumentException(
                    "Nutrition catalog only syncs food data, not user records: " + table
            );
        }
        JSONArray rows = new JSONArray();
        FitnessDatabaseConnection database = this.database;
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", columns) + " FROM " + table +
                        publicationSafePushWhere(table),
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int index = 0; index < columns.length; index++) {
                    putCursorValue(row, columns[index], cursor, index);
                }
                rows.put(row);
            }
        }
        return postRows(config, table, rows);
    }

    /** Public rows are immutable through ordinary sync; publication RPCs own that transition. */
    static String publicationSafePushWhere(String table) {
        if ("nutrition_foods".equals(table)) {
            return " WHERE owner_id = ? AND visibility = 'private'" +
                    " AND lower(COALESCE(source_type, '')) IN " +
                    "('manual', 'manual_estimate', 'manual_option', 'manual_recipe', " +
                    "'pricetrace_manual', 'pricetrace_standard')" +
                    " AND (lower(COALESCE(source_type, '')) <> 'manual_estimate'" +
                    " OR COALESCE(source_reference, '') NOT LIKE '%fitness-nutrition-verified-import.v1%')";
        }
        if ("nutrition_food_nutrients".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = nutrition_food_nutrients.food_id " +
                    "AND parent.visibility = 'private')";
        }
        if ("nutrition_food_components".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = nutrition_food_components.parent_food_id " +
                    "AND parent.visibility = 'private')";
        }
        if ("product_nutrition_links".equals(table)) {
            return " WHERE owner_id = ? AND EXISTS (" +
                    "SELECT 1 FROM nutrition_foods parent " +
                    "WHERE parent.id = product_nutrition_links.nutrition_food_id " +
                    "AND parent.visibility = 'private')";
        }
        return " WHERE owner_id = ?";
    }

    /**
     * Manual rows can be inserted/upserted by the owner. Trusted PriceTrace suggestions are
     * service-role-created, so the app only PATCHes their review state and never re-inserts them.
     */
    private int pushProductLinks(SupabaseConfig config) throws Exception {
        JSONArray deletedManualRows = new JSONArray();
        JSONArray activeManualRows = new JSONArray();
        List<JSONObject> deletedSuggestionRows = new ArrayList<>();
        List<JSONObject> activeSuggestionDecisions = new ArrayList<>();
        FitnessDatabaseConnection database = this.database;
        try (Cursor cursor = database.rawQuery(
                "SELECT " + String.join(", ", PRODUCT_LINK_SYNC_COLUMNS) +
                        " FROM product_nutrition_links" +
                        publicationSafePushWhere("product_nutrition_links"),
                new String[]{userId}
        )) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int index = 0; index < PRODUCT_LINK_SYNC_COLUMNS.length; index++) {
                    putCursorValue(row, PRODUCT_LINK_SYNC_COLUMNS[index], cursor, index);
                }
                String sourceType = nullableString(row, "source_type");
                boolean deleted = nullableString(row, "deleted_at") != null;
                if (ProductNutritionLink.SOURCE_MANUAL.equals(sourceType)) {
                    (deleted ? deletedManualRows : activeManualRows).put(row);
                    continue;
                }
                String status = nullableString(row, "status");
                if (!ProductNutritionLink.STATUS_SUGGESTED.equals(status)
                        || deleted) {
                    (deleted ? deletedSuggestionRows : activeSuggestionDecisions).add(row);
                }
            }
        }

        // Release an existing approved slot before activating its replacement.
        int pushed = postRows(config, "product_nutrition_links", deletedManualRows);
        for (JSONObject decision : deletedSuggestionRows) {
            pushed += patchSuggestionDecision(config, decision);
        }
        pushed += postRows(config, "product_nutrition_links", activeManualRows);
        for (JSONObject decision : activeSuggestionDecisions) {
            pushed += patchSuggestionDecision(config, decision);
        }
        return pushed;
    }

    private int patchSuggestionDecision(SupabaseConfig config, JSONObject row) throws Exception {
        String id = nullableString(row, "id");
        if (id == null) {
            return 0;
        }
        JSONArray remoteRows = getRows(
                config,
                "/rest/v1/product_nutrition_links?owner_id=eq."
                        + encode(config.effectiveUserId()) + "&select=*"
        );
        JSONObject remote = rowsById(remoteRows).get(id);
        if (remote == null || compareRowVersions(row, remote, "revision") <= 0) {
            return 0;
        }
        if (hasActiveApprovedLink(row)) {
            JSONObject conflicting = approvedSlotConflict(remoteRows, row);
            if (conflicting != null
                    && (compareVersions(
                    nullableString(row, "updated_at"),
                    nullableString(conflicting, "updated_at")
            ) <= 0 || retireRemoteApprovedLink(config, conflicting, row) == 0)) {
                return 0;
            }
        }
        return patchRowIfUnchanged(
                config,
                "product_nutrition_links",
                row,
                remote,
                "revision"
        );
    }

    private int pullFoods(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertFoodRows(getRows(
                config,
                "/rest/v1/nutrition_foods?visibility=eq.public&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertFoodRows(getRows(
                    config,
                    "/rest/v1/nutrition_foods?owner_id=eq." + encode(config.effectiveUserId()) +
                            "&select=*"
            ));
        }
        return rows;
    }

    private int pullNutrients(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertNutrientRows(getRows(
                config,
                "/rest/v1/nutrition_food_nutrients?owner_id=is.null&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertNutrientRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_nutrients?owner_id=eq." +
                            encode(config.effectiveUserId()) + "&select=*"
            ));
        }
        return rows;
    }

    private int pullComponents(SupabaseConfig config) throws Exception {
        int rows = 0;
        rows += upsertComponentRows(getRows(
                config,
                "/rest/v1/nutrition_food_components?owner_id=is.null&deleted_at=is.null&select=*"
        ));
        if (config.isConfigured()) {
            rows += upsertComponentRows(getRows(
                    config,
                    "/rest/v1/nutrition_food_components?owner_id=eq." +
                            encode(config.effectiveUserId()) + "&select=*"
            ));
        }
        return rows;
    }

    private int pullProductLinks(SupabaseConfig config) throws Exception {
        return upsertProductLinkRows(getRows(
                config,
                "/rest/v1/product_nutrition_links?owner_id=eq." +
                        encode(config.effectiveUserId()) + "&select=*"
        ));
    }

    private int upsertFoodRows(JSONArray rows) throws JSONException {
        FitnessDatabaseConnection database = this.database;
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String name = nullableString(row, "name");
                if (id == null || name == null) {
                    continue;
                }
                int remoteRevision = Math.max(1, row.optInt("revision", 1));
                String remoteUpdatedAt = nullableString(row, "updated_at");
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_foods",
                        id,
                        remoteRevision,
                        remoteUpdatedAt
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("name", name);
                putNullable(values, "brand", nullableString(row, "brand"));
                putNullable(values, "manufacturer_name", nullableString(row, "manufacturer_name"));
                putNullable(values, "brand_name", nullableString(row, "brand_name"));
                putNullable(values, "sub_brand_name", nullableString(row, "sub_brand_name"));
                putNullable(values, "product_name", nullableString(row, "product_name"));
                putNullableDouble(values, "package_amount", nullableDouble(row, "package_amount"));
                putNullable(values, "package_unit", nullableString(row, "package_unit"));
                if (row.has("package_count") && !row.isNull("package_count")) {
                    values.put("package_count", row.optInt("package_count"));
                } else {
                    values.putNull("package_count");
                }
                String kind = NutritionFood.normalizeKind(
                        row.optString("kind", NutritionFood.KIND_EXTERNAL_MENU));
                values.put("kind", kind);
                values.put("category", NutritionFood.normalizeCategory(
                        row.optString("category", NutritionFood.categoryForKind(kind))));
                values.put("basis_amount", positiveOrDefault(row.optDouble("basis_amount", 1.0)));
                values.put("basis_unit", emptyToDefault(row.optString("basis_unit", "serving"), "serving"));
                values.put("prep_state", NutritionFood.normalizePrepState(
                        row.optString("prep_state", NutritionFood.PREP_UNSPECIFIED)));
                values.put("cooking_method", NutritionFood.normalizeCookingMethod(
                        row.optString("cooking_method", NutritionFood.COOKING_METHOD_UNSPECIFIED)));
                putNullableDouble(values, "calories_kcal", nullableDouble(row, "calories_kcal"));
                putNullableDouble(values, "protein_grams", nullableDouble(row, "protein_grams"));
                putNullableDouble(values, "carbs_grams", nullableDouble(row, "carbs_grams"));
                putNullableDouble(values, "fat_grams", nullableDouble(row, "fat_grams"));
                // 원격에 값이 없으면 0이 아니라 NULL로 남겨 "모름"을 보존한다.
                for (String key : nullableTypedKeys()) {
                    putNullableDouble(values, key, nullableDouble(row, key));
                }
                values.put("source_type", emptyToDefault(row.optString("source_type", "manual"), "manual"));
                putNullable(values, "source_reference", nullableString(row, "source_reference"));
                putNullable(values, "source_version", nullableString(row, "source_version"));
                values.put("data_version", row.optInt(
                        "data_version", NutritionFood.DATA_VERSION_MACROS_ONLY));
                values.put("revision", remoteRevision);
                values.put("visibility", emptyToDefault(row.optString("visibility", "public"), "public"));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_foods",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertNutrientRows(JSONArray rows) throws JSONException {
        FitnessDatabaseConnection database = this.database;
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String foodId = nullableString(row, "food_id");
                String code = NutrientCode.normalize(nullableString(row, "nutrient_code"));
                if (id == null || foodId == null || !NutrientCode.isKnown(code)) {
                    continue;
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_food_nutrients",
                        id,
                        null,
                        nullableString(row, "updated_at")
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("food_id", foodId);
                values.put("nutrient_code", code);
                putNullableDouble(values, "amount", nullableDouble(row, "amount"));
                values.put("unit", NutrientCode.unitOf(code));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_food_nutrients",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertComponentRows(JSONArray rows) throws JSONException {
        FitnessDatabaseConnection database = this.database;
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String parentId = nullableString(row, "parent_food_id");
                String childId = nullableString(row, "child_food_id");
                if (id == null || parentId == null || childId == null) {
                    continue;
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "nutrition_food_components",
                        id,
                        null,
                        nullableString(row, "updated_at")
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                putNullable(values, "owner_id", nullableString(row, "owner_id"));
                values.put("parent_food_id", parentId);
                values.put("child_food_id", childId);
                values.put("quantity", row.optDouble("quantity", 1.0));
                values.put("unit", emptyToDefault(row.optString("unit", "serving"), "serving"));
                values.put("order_index", row.optInt("order_index", 0));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "nutrition_food_components",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private int upsertProductLinkRows(JSONArray rows) throws JSONException {
        FitnessDatabaseConnection database = this.database;
        int applied = 0;
        database.beginTransaction();
        try {
            for (int index = 0; index < rows.length(); index++) {
                JSONObject row = rows.getJSONObject(index);
                String id = nullableString(row, "id");
                String ownerId = nullableString(row, "owner_id");
                String foodId = nullableString(row, "nutrition_food_id");
                String catalogProductId = nullableString(row, "catalog_product_id");
                String status = nullableString(row, "status");
                String sourceType = nullableString(row, "source_type");
                if (id == null || ownerId == null || foodId == null || catalogProductId == null
                        || !isKnownLinkStatus(status) || !isKnownLinkSource(sourceType)) {
                    continue;
                }
                int remoteRevision = Math.max(1, row.optInt("revision", 1));
                String remoteUpdatedAt = nullableString(row, "updated_at");
                if (ProductNutritionLink.STATUS_APPROVED.equals(status)
                        && nullableString(row, "deleted_at") == null) {
                    String localApprovedUpdatedAt = otherApprovedLinkUpdatedAt(
                            database,
                            foodId,
                            id
                    );
                    if (compareVersions(localApprovedUpdatedAt, remoteUpdatedAt) > 0) {
                        continue;
                    }
                    softDeleteApprovedLinks(
                            database,
                            foodId,
                            id,
                            emptyToDefault(remoteUpdatedAt, now())
                    );
                }
                if (!shouldApplyRemoteRow(
                        database,
                        "product_nutrition_links",
                        id,
                        remoteRevision,
                        remoteUpdatedAt
                )) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", id);
                values.put("owner_id", ownerId);
                values.put("nutrition_food_id", foodId);
                values.put("catalog_product_id", catalogProductId);
                putNullable(values, "standard_product_id", nullableString(row, "standard_product_id"));
                values.put("status", status);
                values.put("source_type", sourceType);
                putNullable(values, "proposal_reference", nullableString(row, "proposal_reference"));
                values.put(
                        "product_contract_version",
                        emptyToDefault(
                                nullableString(row, "product_contract_version"),
                                ProductReadV1.CONTRACT_VERSION
                        )
                );
                putNullable(values, "catalog_product_revision", nullableString(row, "catalog_product_revision"));
                putNullableDouble(values, "catalog_content_amount", nullableDouble(row, "catalog_content_amount"));
                putNullable(values, "catalog_content_unit", nullableString(row, "catalog_content_unit"));
                if (row.isNull("catalog_package_count")) {
                    values.putNull("catalog_package_count");
                } else {
                    values.put("catalog_package_count", row.optInt("catalog_package_count"));
                }
                values.put("revision", remoteRevision);
                putNullable(values, "reviewed_at", nullableString(row, "reviewed_at"));
                values.put("created_at", emptyToDefault(row.optString("created_at", ""), now()));
                values.put("updated_at", emptyToDefault(row.optString("updated_at", ""), now()));
                putNullable(values, "deleted_at", nullableString(row, "deleted_at"));
                database.insertWithOnConflict(
                        "product_nutrition_links",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
                applied++;
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return applied;
    }

    private String otherApprovedLinkUpdatedAt(
            FitnessDatabaseConnection database,
            String nutritionFoodId,
            String exceptId
    ) {
        try (Cursor cursor = database.rawQuery(
                "SELECT updated_at FROM product_nutrition_links " +
                        "WHERE owner_id = ? AND nutrition_food_id = ? AND id <> ? " +
                        "AND status = 'approved' AND deleted_at IS NULL " +
                        "ORDER BY updated_at DESC LIMIT 1",
                new String[]{userId, nutritionFoodId, exceptId}
        )) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : null;
        }
    }

    private ContentValues foodValues(NutritionFood food, String timestamp) {
        ContentValues values = new ContentValues();
        values.put("id", food.id);
        values.put("owner_id", food.ownerId);
        values.put("name", food.name);
        putNullable(values, "brand", food.brand);
        putNullable(values, "manufacturer_name", food.manufacturerName);
        putNullable(values, "brand_name", food.brandName);
        putNullable(values, "sub_brand_name", food.subBrandName);
        putNullable(values, "product_name", food.productName);
        putNullableDouble(values, "package_amount", food.packageAmount);
        putNullable(values, "package_unit", food.packageUnit);
        if (food.packageCount == null) {
            values.putNull("package_count");
        } else {
            values.put("package_count", food.packageCount);
        }
        values.put("kind", food.kind);
        values.put("category", food.category);
        values.put("basis_amount", food.basisAmount);
        values.put("basis_unit", food.basisUnit);
        values.put("prep_state", food.prepState);
        values.put("cooking_method", food.cookingMethod);
        putNullableDouble(values, "calories_kcal", food.profile.value(NutritionProfile.CALORIES_KCAL));
        putNullableDouble(values, "protein_grams", food.profile.value(NutritionProfile.PROTEIN_GRAMS));
        putNullableDouble(values, "carbs_grams", food.profile.value(NutritionProfile.CARBS_GRAMS));
        putNullableDouble(values, "fat_grams", food.profile.value(NutritionProfile.FAT_GRAMS));
        for (String key : nullableTypedKeys()) {
            putNullableDouble(values, key, food.profile.value(key));
        }
        values.put("source_type", food.sourceType);
        putNullable(values, "source_reference", food.sourceReference);
        putNullable(values, "source_version", food.sourceVersion);
        values.put("data_version", food.dataVersion);
        values.put("revision", food.revision);
        values.put("visibility", "private");
        values.put("created_at", timestamp);
        values.put("updated_at", timestamp);
        values.putNull("deleted_at");
        return values;
    }

    private NutritionFoodsRoomEntity foodEntity(NutritionFood food, String timestamp) {
        return foodEntity(food, timestamp, timestamp);
    }

    private NutritionFoodsRoomEntity foodEntity(
            NutritionFood food,
            String createdAt,
            String updatedAt
    ) {
        return new NutritionFoodsRoomEntity(
                food.id,
                food.ownerId,
                food.name,
                food.brand,
                food.manufacturerName,
                food.brandName,
                food.subBrandName,
                food.productName,
                food.packageAmount,
                food.packageUnit,
                food.packageCount == null ? null : food.packageCount.longValue(),
                food.kind,
                food.category,
                food.basisAmount,
                food.basisUnit,
                food.prepState,
                food.cookingMethod,
                food.profile.value(NutritionProfile.CALORIES_KCAL),
                food.profile.value(NutritionProfile.PROTEIN_GRAMS),
                food.profile.value(NutritionProfile.CARBS_GRAMS),
                food.profile.value(NutritionProfile.FAT_GRAMS),
                food.profile.value(NutritionProfile.SODIUM_MG),
                food.profile.value(NutritionProfile.SATURATED_FAT_GRAMS),
                food.profile.value(NutritionProfile.SUGARS_GRAMS),
                food.profile.value(NutritionProfile.FIBER_GRAMS),
                food.profile.value(NutritionProfile.ADDED_SUGARS_GRAMS),
                food.profile.value(NutritionProfile.TRANS_FAT_GRAMS),
                food.profile.value(NutritionProfile.CHOLESTEROL_MG),
                food.sourceType,
                food.sourceReference,
                food.sourceVersion,
                (long) food.dataVersion,
                (long) food.revision,
                "private",
                createdAt,
                updatedAt,
                null
        );
    }

    private void cachePriceTraceProductRoom(ProductReadV1 product, String fetchedAt) {
        nutritionDao.upsertProductCache(new PricetraceProductCacheRoomEntity(
                product.catalogProductId,
                product.standardProductId,
                product.name,
                product.brand,
                product.manufacturerName,
                product.subBrandName,
                product.sellerName,
                product.latestObservedPriceKrw == null
                        ? null : product.latestObservedPriceKrw.longValue(),
                product.observedAt,
                product.contentAmount,
                product.contentUnit,
                product.packageCount == null ? null : product.packageCount.longValue(),
                product.revision,
                ProductReadV1.CONTRACT_VERSION,
                fetchedAt
        ));
    }

    private void replaceMicronutrientsRoom(NutritionFood food) {
        nutritionDao.deleteNutrients(food.id);
        String timestamp = now();
        for (String code : food.profile.knownMicronutrientCodes()) {
            nutritionDao.upsertNutrient(new NutritionFoodNutrientsRoomEntity(
                    UUID.randomUUID().toString(),
                    food.ownerId,
                    food.id,
                    code,
                    food.profile.value(code),
                    NutrientCode.unitOf(code),
                    timestamp,
                    timestamp,
                    null
            ));
        }
    }

    private void replaceMicronutrients(FitnessDatabaseConnection database, NutritionFood food) {
        database.delete("nutrition_food_nutrients", "food_id = ?", new String[]{food.id});
        String timestamp = now();
        for (String code : food.profile.knownMicronutrientCodes()) {
            ContentValues values = new ContentValues();
            values.put("id", UUID.randomUUID().toString());
            values.put("owner_id", food.ownerId);
            values.put("food_id", food.id);
            values.put("nutrient_code", code);
            values.put("amount", food.profile.value(code));
            values.put("unit", NutrientCode.unitOf(code));
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

    /** 필수 7종이 다 있는지 확인하고, 없으면 무엇이 빠졌는지 알려 준다. */
    private static NutritionProfile requireRequiredNutrients(NutritionProfile profile) {
        NutritionProfile safeProfile = profile == null ? NutritionProfile.empty() : profile;
        List<String> missing = safeProfile.missingRequiredKeys();
        if (!missing.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (String key : missing) {
                labels.add(NutritionProfile.labelOf(key));
            }
            throw new IllegalArgumentException(
                    "필수 영양소를 모두 입력하세요: " + String.join(", ", labels)
            );
        }
        return safeProfile;
    }

    private static List<String> nullableTypedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(NutritionProfile.SODIUM_MG);
        keys.add(NutritionProfile.SATURATED_FAT_GRAMS);
        keys.add(NutritionProfile.SUGARS_GRAMS);
        keys.addAll(NutritionProfile.RECOMMENDED_TYPED_KEYS);
        return keys;
    }

    private static String[] syncColumns() {
        List<String> columns = new ArrayList<>(java.util.Arrays.asList(FOOD_COLUMNS));
        columns.add("visibility");
        columns.add("created_at");
        columns.add("updated_at");
        columns.add("deleted_at");
        return columns.toArray(new String[0]);
    }

    private boolean shouldApplyRemoteRow(
            FitnessDatabaseConnection database,
            String table,
            String id,
            Integer remoteRevision,
            String remoteUpdatedAt
    ) {
        String select = remoteRevision == null ? "updated_at" : "revision, updated_at";
        try (Cursor cursor = database.rawQuery(
                "SELECT " + select + " FROM " + table + " WHERE id = ? LIMIT 1",
                new String[]{id}
        )) {
            if (!cursor.moveToFirst()) {
                return true;
            }
            int updatedAtIndex = remoteRevision == null ? 0 : 1;
            if (remoteRevision != null) {
                int localRevision = cursor.getInt(0);
                if (remoteRevision > localRevision) {
                    return true;
                }
                if (remoteRevision < localRevision) {
                    return false;
                }
            }
            String localUpdatedAt = cursor.isNull(updatedAtIndex)
                    ? null
                    : cursor.getString(updatedAtIndex);
            return compareVersions(remoteUpdatedAt, localUpdatedAt) > 0;
        }
    }

    static int compareVersions(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        try {
            return OffsetDateTime.parse(left).toInstant()
                    .compareTo(OffsetDateTime.parse(right).toInstant());
        } catch (Exception ignored) {
            return left.compareTo(right);
        }
    }

    private int postRows(SupabaseConfig config, String table, JSONArray rows) throws Exception {
        if (rows.length() == 0) {
            return 0;
        }
        JSONArray remoteRows = getRows(
                config,
                "/rest/v1/" + table + "?owner_id=eq."
                        + encode(config.effectiveUserId()) + "&select=*"
        );
        Map<String, JSONObject> remoteById = rowsById(remoteRows);
        int pushed = 0;
        for (int index = 0; index < rows.length(); index++) {
            JSONObject local = rows.getJSONObject(index);
            String id = nullableString(local, "id");
            if (id == null) {
                continue;
            }
            JSONObject remote = remoteById.get(id);
            if (remote == null) {
                if ("product_nutrition_links".equals(table)
                        && hasActiveApprovedLink(local)) {
                    JSONObject conflicting = approvedSlotConflict(remoteRows, local);
                    if (conflicting != null) {
                        if (compareVersions(
                                nullableString(local, "updated_at"),
                                nullableString(conflicting, "updated_at")
                        ) <= 0 || retireRemoteApprovedLink(config, conflicting, local) == 0) {
                            continue;
                        }
                    }
                }
                JSONObject insertPayload = local;
                if ("product_nutrition_links".equals(table)) {
                    // The remote RLS contract does not allow a client INSERT to claim
                    // trusted PriceTrace specification metadata. Insert the owner-approved
                    // link first, then apply the locally verified metadata through the
                    // authenticated UPDATE path below.
                    insertPayload = directInsertProductLinkPayload(local);
                }
                int inserted = insertRowIfAbsent(config, table, insertPayload);
                pushed += inserted;
                if (inserted > 0
                        && "product_nutrition_links".equals(table)) {
                    patchRowIfUnchanged(config, table, local, insertPayload, "revision");
                }
                continue;
            }

            String versionKey = usesRevision(table) ? "revision" : "updated_at";
            if (compareRowVersions(local, remote, versionKey) <= 0) {
                continue;
            }
            pushed += patchRowIfUnchanged(config, table, local, remote, versionKey);
        }
        return pushed;
    }

    private JSONArray getRows(SupabaseConfig config, String path) throws Exception {
        final int pageSize = 500;
        JSONArray allRows = new JSONArray();
        for (int offset = 0; ; offset += pageSize) {
            String separator = path.contains("?") ? "&" : "?";
            String pagedPath = path + separator + "order=id.asc&limit=" + pageSize
                    + "&offset=" + offset;
            HttpURLConnection connection = openConnection(
                    joinUrl(config.supabaseUrl, pagedPath),
                    "GET",
                    config
            );
            connection.setRequestProperty("Accept", "application/json");
            String body = readResponseOrThrow(connection, 200, 206);
            JSONArray page = body.isEmpty() ? new JSONArray() : new JSONArray(body);
            for (int index = 0; index < page.length(); index++) {
                allRows.put(page.get(index));
            }
            if (page.length() < pageSize) {
                return allRows;
            }
        }
    }

    private Map<String, JSONObject> rowsById(JSONArray rows) throws JSONException {
        Map<String, JSONObject> indexed = new LinkedHashMap<>();
        for (int index = 0; index < rows.length(); index++) {
            JSONObject row = rows.getJSONObject(index);
            String id = nullableString(row, "id");
            if (id != null) {
                indexed.put(id, row);
            }
        }
        return indexed;
    }

    private boolean usesRevision(String table) {
        return "nutrition_foods".equals(table)
                || "product_nutrition_links".equals(table);
    }

    private int compareRowVersions(JSONObject local, JSONObject remote, String versionKey) {
        if ("revision".equals(versionKey)) {
            int revisionComparison = Integer.compare(
                    Math.max(1, local.optInt("revision", 1)),
                    Math.max(1, remote.optInt("revision", 1))
            );
            if (revisionComparison != 0) {
                return revisionComparison;
            }
        }
        return compareVersions(
                nullableString(local, "updated_at"),
                nullableString(remote, "updated_at")
        );
    }

    private int insertRowIfAbsent(
            SupabaseConfig config,
            String table,
            JSONObject row
    ) throws Exception {
        HttpURLConnection connection = openConnection(
                joinUrl(config.supabaseUrl, "/rest/v1/" + table + "?on_conflict=id"),
                "POST",
                config
        );
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty(
                "Prefer",
                "resolution=ignore-duplicates,return=representation"
        );
        connection.setDoOutput(true);
        JSONArray payload = new JSONArray();
        payload.put(row);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200, 201);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private int patchRowIfUnchanged(
            SupabaseConfig config,
            String table,
            JSONObject local,
            JSONObject remote,
            String versionKey
    ) throws Exception {
        String expected = "revision".equals(versionKey)
                ? String.valueOf(Math.max(1, remote.optInt("revision", 1)))
                : nullableString(remote, "updated_at");
        String filter = expected == null ? "is.null" : "eq." + encode(expected);
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/" + table
                        + "?id=eq." + encode(local.getString("id"))
                        + "&owner_id=eq." + encode(config.effectiveUserId())
                        + "&" + versionKey + "=" + filter
        );
        HttpURLConnection connection = openConnection(endpoint, "PATCH", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoOutput(true);
        JSONObject patch = new JSONObject(local.toString());
        patch.remove("id");
        patch.remove("owner_id");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(patch.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private boolean hasActiveApprovedLink(JSONObject row) {
        return ProductNutritionLink.STATUS_APPROVED.equals(nullableString(row, "status"))
                && nullableString(row, "deleted_at") == null;
    }

    static JSONObject directInsertProductLinkPayload(JSONObject row) throws JSONException {
        JSONObject payload = new JSONObject(row.toString());
        payload.put("catalog_product_revision", JSONObject.NULL);
        payload.put("catalog_content_amount", JSONObject.NULL);
        payload.put("catalog_content_unit", JSONObject.NULL);
        payload.put("catalog_package_count", JSONObject.NULL);
        return payload;
    }

    private JSONObject approvedSlotConflict(JSONArray remoteRows, JSONObject local)
            throws JSONException {
        String foodId = nullableString(local, "nutrition_food_id");
        String localId = nullableString(local, "id");
        for (int index = 0; index < remoteRows.length(); index++) {
            JSONObject remote = remoteRows.getJSONObject(index);
            if (hasActiveApprovedLink(remote)
                    && foodId != null
                    && foodId.equals(nullableString(remote, "nutrition_food_id"))
                    && !localId.equals(nullableString(remote, "id"))) {
                return remote;
            }
        }
        return null;
    }

    private int retireRemoteApprovedLink(
            SupabaseConfig config,
            JSONObject remote,
            JSONObject localReplacement
    ) throws Exception {
        String replacementTimestamp = emptyToDefault(
                nullableString(localReplacement, "updated_at"),
                now()
        );
        String endpoint = joinUrl(
                config.supabaseUrl,
                "/rest/v1/product_nutrition_links"
                        + "?id=eq." + encode(remote.getString("id"))
                        + "&owner_id=eq." + encode(config.effectiveUserId())
                        + "&revision=eq." + Math.max(1, remote.optInt("revision", 1))
        );
        HttpURLConnection connection = openConnection(endpoint, "PATCH", config);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        connection.setDoOutput(true);
        JSONObject patch = new JSONObject();
        patch.put("deleted_at", replacementTimestamp);
        patch.put("updated_at", replacementTimestamp);
        patch.put("revision", Math.max(1, remote.optInt("revision", 1)) + 1);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(patch.toString().getBytes(StandardCharsets.UTF_8));
        }
        String body = readResponseOrThrow(connection, 200);
        return body.isEmpty() ? 0 : new JSONArray(body).length();
    }

    private HttpURLConnection openConnection(String endpoint, String method, SupabaseConfig config)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("apikey", config.supabaseAnonKey);
        String bearer = config.accessToken.isEmpty() ? config.supabaseAnonKey : config.accessToken;
        connection.setRequestProperty("Authorization", "Bearer " + bearer);
        return connection;
    }

    private String readResponseOrThrow(HttpURLConnection connection, int... okCodes) throws IOException {
        int statusCode = connection.getResponseCode();
        for (int okCode : okCodes) {
            if (statusCode == okCode) {
                return readStream(connection.getInputStream());
            }
        }
        throw new IOException(
                "Nutrition catalog sync failed (" + statusCode + "): " +
                        readStream(connection.getErrorStream())
        );
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void putCursorValue(JSONObject row, String name, Cursor cursor, int index)
            throws JSONException {
        if (cursor.isNull(index)) {
            row.put(name, JSONObject.NULL);
            return;
        }
        switch (cursor.getType(index)) {
            case Cursor.FIELD_TYPE_INTEGER:
                row.put(name, cursor.getLong(index));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                row.put(name, cursor.getDouble(index));
                break;
            default:
                row.put(name, cursor.getString(index));
                break;
        }
    }

    private static double positiveOrDefault(Double value) {
        return value == null || value <= 0 ? 1.0 : value;
    }

    private static String normalizePackagedUnit(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = NutritionUnit.normalize(value);
        if ("each".equals(normalized)) {
            return normalized;
        }
        return NutritionUnit.requireSupported(normalized);
    }

    private static String nullableString(JSONObject object, String key) {
        return !object.has(key) || object.isNull(key) ? null : object.optString(key, null);
    }

    private static void putNullable(JSONObject object, String key, String value)
            throws JSONException {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static String requireReturnedUuid(JSONObject object, String key) throws IOException {
        String value = nullableString(object, key);
        if (value == null) {
            throw new IOException("PT 등록 응답에 " + key + "가 없습니다.");
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IOException("PT 등록 응답의 " + key + "가 UUID가 아닙니다.", error);
        }
    }

    private static Double nullableDouble(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key)) {
            return null;
        }
        double value = object.optDouble(key, Double.NaN);
        return Double.isNaN(value) ? null : value;
    }

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    private static String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? SupabaseConfig.DEFAULT_USER_ID : normalized;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static final class ExistingCatalogRow {
        private final String id;
        private final String createdAt;

        private ExistingCatalogRow(String id, String createdAt) {
            this.id = id;
            this.createdAt = createdAt;
        }
    }

    /**
     * Identity projection for an external menu. It intentionally ignores branch, date,
     * record IDs, and composition selections so those values cannot create menu duplicates.
     */
    private static final class DiningOutMenuCanonicalIdentity {
        private final String restaurantMenuId;
        private final String normalizedStoreName;
        private final String normalizedMenuName;

        private DiningOutMenuCanonicalIdentity(
                String restaurantMenuId,
                String normalizedStoreName,
                String normalizedMenuName
        ) {
            this.restaurantMenuId = restaurantMenuId;
            this.normalizedStoreName = normalizedStoreName;
            this.normalizedMenuName = normalizedMenuName;
        }

        private static DiningOutMenuCanonicalIdentity from(
                String storeName,
                String menuName,
                String sourceReference
        ) {
            JSONObject source = null;
            if (sourceReference != null && !sourceReference.trim().isEmpty()) {
                try {
                    source = new JSONObject(sourceReference);
                } catch (JSONException ignored) {
                    // The catalog row still has its name/brand fallback identity.
                }
            }
            String sourceStoreName = source == null
                    ? null
                    : firstNonBlank(
                            nullableString(source, "normalized_store_name"),
                            nullableString(source, "restaurant_name")
                    );
            String sourceMenuName = source == null
                    ? null
                    : firstNonBlank(
                            nullableString(source, "normalized_menu_name"),
                            nullableString(source, "menu_name")
                    );
            String sourceRestaurantMenuId = source == null
                    ? null
                    : nullableString(source, "restaurant_menu_id");
            return new DiningOutMenuCanonicalIdentity(
                    normalizeIdentityToken(sourceRestaurantMenuId),
                    normalizeDiningOutIdentityText(firstNonBlank(storeName, sourceStoreName)),
                    normalizeDiningOutIdentityText(firstNonBlank(menuName, sourceMenuName))
            );
        }

        private String key() {
            if (!restaurantMenuId.isEmpty()) {
                return "restaurant_menu_id|" + restaurantMenuId;
            }
            return "store|" + normalizedStoreName + "|menu|" + normalizedMenuName;
        }

        private boolean matches(DiningOutMenuCanonicalIdentity candidate) {
            if (!restaurantMenuId.isEmpty() || !candidate.restaurantMenuId.isEmpty()) {
                return !restaurantMenuId.isEmpty()
                        && restaurantMenuId.equals(candidate.restaurantMenuId);
            }
            return normalizedStoreName.equals(candidate.normalizedStoreName)
                    && normalizedMenuName.equals(candidate.normalizedMenuName);
        }

        private static String firstNonBlank(String primary, String fallback) {
            String normalizedPrimary = normalizeNullableText(primary);
            return normalizedPrimary == null ? fallback : normalizedPrimary;
        }

        private static String normalizeIdentityToken(String value) {
            String normalized = normalizeNullableText(value);
            if (normalized == null || "null".equalsIgnoreCase(normalized)) {
                return "";
            }
            return normalized.toLowerCase(Locale.ROOT);
        }

        private static String normalizeDiningOutIdentityText(String value) {
            String normalized = value == null ? "" : value.trim();
            return normalized.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }

        private static String normalizeNullableText(String value) {
            String normalized = value == null ? "" : value.trim();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    private static boolean isKnownLinkStatus(String value) {
        return ProductNutritionLink.STATUS_SUGGESTED.equals(value)
                || ProductNutritionLink.STATUS_APPROVED.equals(value)
                || ProductNutritionLink.STATUS_REJECTED.equals(value);
    }

    private static boolean isKnownLinkSource(String value) {
        return ProductNutritionLink.SOURCE_MANUAL.equals(value)
                || ProductNutritionLink.SOURCE_PRICETRACE.equals(value);
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

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    public static final class RecipeComponent {
        public final NutritionFood food;
        public final double quantity;
        public final String unit;

        private RecipeComponent(NutritionFood food, double quantity, String unit) {
            this.food = food;
            this.quantity = quantity;
            this.unit = unit;
        }
    }

    public interface SyncCallback {
        void onComplete(int pushedRows, int pulledRows);

        void onError(Exception error);
    }

    public interface PublicationCallback {
        void onComplete(PublicationState state);

        void onError(Exception error);
    }

    public static final class PublicationState {
        public final String nutritionFoodId;
        public final String catalogProductId;
        public final boolean isPublic;
        public final int publicationRevision;
        public final String publishedAt;

        PublicationState(
                String nutritionFoodId,
                String catalogProductId,
                boolean isPublic,
                int publicationRevision,
                String publishedAt
        ) {
            this.nutritionFoodId = nutritionFoodId;
            this.catalogProductId = catalogProductId;
            this.isPublic = isPublic;
            this.publicationRevision = publicationRevision;
            this.publishedAt = publishedAt;
        }
    }

    public static final class CatalogSyncResult {
        public final int pushedRows;
        public final int pulledRows;

        public CatalogSyncResult(int pushedRows, int pulledRows) {
            this.pushedRows = pushedRows;
            this.pulledRows = pulledRows;
        }
    }
}
