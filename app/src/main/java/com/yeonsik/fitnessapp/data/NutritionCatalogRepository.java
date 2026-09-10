package com.yeonsik.fitnessapp.data;

import android.content.Context;

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
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogSyncStore;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogBackupApi;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionCatalogSyncSnapshot;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionComponentSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionNutrientSyncRow;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionProductLinkSyncRow;

import org.json.JSONException;
import org.json.JSONObject;

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
 * <p>The app can record and search foods without a network connection. Remote publication and
 * catalog synchronization are integration concerns; this repository exposes only typed local
 * catalog operations to those clients.</p>
 *
 * <p>이 저장소는 <b>음식·레시피·영양성분만</b> 다룬다. 원격 테이블은 FitnessApp 전용
 * Nutrition Supabase 프로젝트에 있으며, Personal OS 공통 DB의 meal_records 같은 사용자
 * 섭취 기록은 이 카탈로그에 절대 들어가지 않는다.</p>
 */
public final class NutritionCatalogRepository implements
        com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi,
        NutritionCatalogSyncStore,
        NutritionCatalogBackupApi {
    /** 영양 전용 DB의 카탈로그 테이블. 공통 사용자 기록 테이블은 여기 들어올 수 없다. */
    static final List<String> CATALOG_TABLES = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "nutrition_foods",
                    "nutrition_food_nutrients",
                    "nutrition_food_components",
                    "product_nutrition_links"
            )
    );

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

    /** Stable sync contract allowlist retained for codec and policy tests. */
    static final String[] PRODUCT_LINK_SYNC_COLUMNS = {
            "id", "owner_id", "nutrition_food_id", "catalog_product_id", "standard_product_id", "status",
            "source_type", "proposal_reference", "product_contract_version",
            "catalog_product_revision", "catalog_content_amount", "catalog_content_unit",
            "catalog_package_count", "revision",
            "reviewed_at", "created_at", "updated_at", "deleted_at"
    };

    private final FitnessRoomDatabase roomDatabase;
    private final NutritionRoomDao nutritionDao;
    private final Context applicationContext;
    private volatile String userId;

    public NutritionCatalogRepository(
            FitnessDatabaseHelper dbHelper,
            String userId,
            Object ignoredNetworkConfig
    ) {
        this(
                FitnessRoomDatabaseProvider.get(dbHelper.applicationContext()),
                userId,
                ignoredNetworkConfig,
                dbHelper.applicationContext()
        );
    }

    public NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            String userId,
            Object ignoredNetworkConfig
    ) {
        this(
                roomDatabase,
                userId,
                ignoredNetworkConfig,
                null
        );
    }

    private NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            String userId,
            Object ignoredNetworkConfig,
            Context context
    ) {
        this.roomDatabase = roomDatabase;
        this.nutritionDao = roomDatabase.nutritionRoomDao();
        this.applicationContext = context == null ? null : context.getApplicationContext();
        this.userId = normalizeUserId(userId);
    }

    public NutritionCatalogRepository(
            FitnessRoomDatabase roomDatabase,
            android.content.Context context,
            String userId,
            Object ignoredNetworkConfig
    ) {
        this(
                roomDatabase,
                userId,
                ignoredNetworkConfig,
                context
        );
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    @Override
    public void reconcileVerifiedFoodCatalog() {
        if (applicationContext == null) {
            throw new IllegalStateException("Nutrition catalog context is required for seed.");
        }
        VerifiedFoodCatalogSeed.seedWithRoom(applicationContext, roomDatabase);
    }

    @Override
    public boolean applyPublicationVisibility(
            String foodId,
            String ownerId,
            String visibility,
            String updatedAt
    ) {
        return nutritionDao.updateVisibility(
                requireName(foodId),
                normalizeUserId(ownerId),
                requireName(visibility),
                requireName(updatedAt)
        ) == 1;
    }

    @Override
    public boolean applySourceReference(
            String foodId,
            String ownerId,
            String sourceReference,
            String updatedAt
    ) {
        if (sourceReference == null || sourceReference.trim().isEmpty()) {
            return false;
        }
        return nutritionDao.updateSourceReference(
                requireName(foodId),
                normalizeUserId(ownerId),
                sourceReference,
                requireName(updatedAt)
        ) == 1;
    }

    public void normalizeLocalUserId(String nextUserId) {
        String normalizedNextUserId = normalizeUserId(nextUserId);
        String previousUserId = userId;
        if (AccountOwnerPolicy.shouldClaimLocalRows(
                previousUserId,
                normalizedNextUserId
        )) {
            roomDatabase.runInTransaction(() -> {
                resolveApprovedLinkClaimConflicts(
                        SupabaseConfig.DEFAULT_USER_ID,
                        normalizedNextUserId
                );
                nutritionDao.claimFoodRows(SupabaseConfig.DEFAULT_USER_ID, normalizedNextUserId);
                nutritionDao.claimNutrientRows(
                        SupabaseConfig.DEFAULT_USER_ID,
                        normalizedNextUserId
                );
                nutritionDao.claimComponentRows(
                        SupabaseConfig.DEFAULT_USER_ID,
                        normalizedNextUserId
                );
                nutritionDao.claimProductLinkRows(
                        SupabaseConfig.DEFAULT_USER_ID,
                        normalizedNextUserId
                );
            });
        }
        userId = normalizedNextUserId;
    }

    private void resolveApprovedLinkClaimConflicts(
            String sourceUserId,
            String nextUserId
    ) {
        for (NutritionRoomDao.ApprovedLinkClaimRow conflict
                : nutritionDao.approvedLinkClaimConflicts(sourceUserId, nextUserId)) {
            boolean localWins = compareVersions(
                    conflict.getSourceUpdatedAt(),
                    conflict.getTargetUpdatedAt()
            ) > 0;
            String losingId = localWins
                    ? conflict.getTargetId()
                    : conflict.getSourceId();
            String winningTimestamp = localWins
                    ? conflict.getSourceUpdatedAt()
                    : conflict.getTargetUpdatedAt();
            nutritionDao.retireClaimConflict(losingId, winningTimestamp);
        }
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

    /** Pending owner-specific suggestions written by a trusted PriceTrace integration. */
    public List<ProductNutritionLink> pendingProductLinkSuggestions(String nutritionFoodId) {
        return readProductLinks(nutritionFoodId, ProductNutritionLink.STATUS_SUGGESTED);
    }

    @Override
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

    private void requirePriceTraceCatalogMetadata(ProductReadV1 product) {
        if (!product.hasValidPriceTraceCatalogMetadata()) {
            throw new IllegalArgumentException(
                    "PriceTrace product-read.v1의 상품별 revision·규격값이 없거나 허용값이 아닙니다."
            );
        }
    }

    @Override
    public NutritionCatalogSyncSnapshot exportSyncSnapshot(String ownerId) {
        String normalizedOwnerId = normalizeUserId(ownerId);
        return new NutritionCatalogSyncSnapshot(
                readFoodSyncRows(normalizedOwnerId),
                readNutrientSyncRows(normalizedOwnerId),
                readComponentSyncRows(normalizedOwnerId),
                readProductLinkSyncRows(normalizedOwnerId)
        );
    }

    private List<NutritionFoodSyncRow> readFoodSyncRows(String ownerId) {
        List<NutritionFoodSyncRow> rows = new ArrayList<>();
        for (NutritionFoodsRoomEntity row : nutritionDao.syncFoods(ownerId)) {
            rows.add(new NutritionFoodSyncRow(
                    row.getId(),
                    row.getOwnerId(),
                    row.getName(),
                    row.getBrand(),
                    row.getManufacturerName(),
                    row.getBrandName(),
                    row.getSubBrandName(),
                    row.getProductName(),
                    row.getPackageAmount(),
                    row.getPackageUnit(),
                    row.getPackageCount(),
                    row.getKind(),
                    row.getCategory(),
                    row.getBasisAmount(),
                    row.getBasisUnit(),
                    row.getPrepState(),
                    row.getCookingMethod(),
                    row.getCaloriesKcal(),
                    row.getProteinGrams(),
                    row.getCarbsGrams(),
                    row.getFatGrams(),
                    row.getSodiumMg(),
                    row.getSaturatedFatGrams(),
                    row.getSugarsGrams(),
                    row.getFiberGrams(),
                    row.getAddedSugarsGrams(),
                    row.getTransFatGrams(),
                    row.getCholesterolMg(),
                    row.getSourceType(),
                    row.getSourceReference(),
                    row.getSourceVersion(),
                    (int) row.getDataVersion(),
                    (int) row.getRevision(),
                    row.getVisibility(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    row.getDeletedAt()
            ));
        }
        return rows;
    }

    private List<NutritionNutrientSyncRow> readNutrientSyncRows(String ownerId) {
        List<NutritionNutrientSyncRow> rows = new ArrayList<>();
        for (NutritionFoodNutrientsRoomEntity row : nutritionDao.syncNutrients(ownerId)) {
            rows.add(new NutritionNutrientSyncRow(
                    row.getId(),
                    row.getOwnerId(),
                    row.getFoodId(),
                    row.getNutrientCode(),
                    row.getAmount(),
                    row.getUnit(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    row.getDeletedAt()
            ));
        }
        return rows;
    }

    private List<NutritionComponentSyncRow> readComponentSyncRows(String ownerId) {
        List<NutritionComponentSyncRow> rows = new ArrayList<>();
        for (NutritionFoodComponentsRoomEntity row : nutritionDao.syncComponents(ownerId)) {
            rows.add(new NutritionComponentSyncRow(
                    row.getId(),
                    row.getOwnerId(),
                    row.getParentFoodId(),
                    row.getChildFoodId(),
                    row.getQuantity(),
                    row.getUnit(),
                    row.getOrderIndex(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    row.getDeletedAt()
            ));
        }
        return rows;
    }

    private List<NutritionProductLinkSyncRow> readProductLinkSyncRows(String ownerId) {
        List<NutritionProductLinkSyncRow> rows = new ArrayList<>();
        for (ProductNutritionLinksRoomEntity row : nutritionDao.syncProductLinks(ownerId)) {
            rows.add(new NutritionProductLinkSyncRow(
                    row.getId(),
                    row.getOwnerId(),
                    row.getNutritionFoodId(),
                    row.getCatalogProductId(),
                    row.getStandardProductId(),
                    row.getStatus(),
                    row.getSourceType(),
                    row.getProposalReference(),
                    row.getProductContractVersion(),
                    row.getCatalogProductRevision(),
                    row.getCatalogContentAmount(),
                    row.getCatalogContentUnit(),
                    row.getCatalogPackageCount(),
                    (int) row.getRevision(),
                    row.getReviewedAt(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    row.getDeletedAt()
            ));
        }
        return rows;
    }

    @Override
    public int applyRemoteFoodRows(List<NutritionFoodSyncRow> rows) {
        final int[] applied = {0};
        roomDatabase.runInTransaction(() -> {
            for (NutritionFoodSyncRow row : rows) {
                if (row == null || row.getId() == null || row.getName() == null
                        || !shouldApplyRemoteRoomRow(
                        "nutrition_foods", row.getId(), row.getRevision(), row.getUpdatedAt())) {
                    continue;
                }
                String kind = NutritionFood.normalizeKind(
                        emptyToDefault(row.getKind(), NutritionFood.KIND_EXTERNAL_MENU)
                );
                nutritionDao.upsertFood(new NutritionFoodsRoomEntity(
                        row.getId(),
                        row.getOwnerId(),
                        row.getName(),
                        row.getBrand(),
                        row.getManufacturerName(),
                        row.getBrandName(),
                        row.getSubBrandName(),
                        row.getProductName(),
                        row.getPackageAmount(),
                        row.getPackageUnit(),
                        row.getPackageCount(),
                        kind,
                        NutritionFood.normalizeCategory(
                                emptyToDefault(row.getCategory(), NutritionFood.categoryForKind(kind))
                        ),
                        positiveOrDefault(row.getBasisAmount()),
                        emptyToDefault(row.getBasisUnit(), "serving"),
                        NutritionFood.normalizePrepState(
                                emptyToDefault(row.getPrepState(), NutritionFood.PREP_UNSPECIFIED)
                        ),
                        NutritionFood.normalizeCookingMethod(
                                emptyToDefault(row.getCookingMethod(), NutritionFood.COOKING_METHOD_UNSPECIFIED)
                        ),
                        row.getCaloriesKcal(),
                        row.getProteinGrams(),
                        row.getCarbsGrams(),
                        row.getFatGrams(),
                        row.getSodiumMg(),
                        row.getSaturatedFatGrams(),
                        row.getSugarsGrams(),
                        row.getFiberGrams(),
                        row.getAddedSugarsGrams(),
                        row.getTransFatGrams(),
                        row.getCholesterolMg(),
                        emptyToDefault(row.getSourceType(), "manual"),
                        row.getSourceReference(),
                        row.getSourceVersion(),
                        row.getDataVersion(),
                        row.getRevision(),
                        emptyToDefault(row.getVisibility(), "public"),
                        emptyToDefault(row.getCreatedAt(), now()),
                        emptyToDefault(row.getUpdatedAt(), now()),
                        row.getDeletedAt()
                ));
                applied[0]++;
            }
        });
        return applied[0];
    }

    @Override
    public int applyRemoteNutrientRows(List<NutritionNutrientSyncRow> rows) {
        final int[] applied = {0};
        roomDatabase.runInTransaction(() -> {
            for (NutritionNutrientSyncRow row : rows) {
                String code = row == null ? null : NutrientCode.normalize(row.getNutrientCode());
                if (row == null || row.getId() == null || row.getFoodId() == null
                        || !NutrientCode.isKnown(code)
                        || !shouldApplyRemoteRoomRow(
                        "nutrition_food_nutrients", row.getId(), null, row.getUpdatedAt())) {
                    continue;
                }
                nutritionDao.upsertNutrient(new NutritionFoodNutrientsRoomEntity(
                        row.getId(),
                        row.getOwnerId(),
                        row.getFoodId(),
                        code,
                        row.getAmount(),
                        NutrientCode.unitOf(code),
                        emptyToDefault(row.getCreatedAt(), now()),
                        emptyToDefault(row.getUpdatedAt(), now()),
                        row.getDeletedAt()
                ));
                applied[0]++;
            }
        });
        return applied[0];
    }

    @Override
    public int applyRemoteComponentRows(List<NutritionComponentSyncRow> rows) {
        final int[] applied = {0};
        roomDatabase.runInTransaction(() -> {
            for (NutritionComponentSyncRow row : rows) {
                if (row == null || row.getId() == null || row.getParentFoodId() == null
                        || row.getChildFoodId() == null
                        || !shouldApplyRemoteRoomRow(
                        "nutrition_food_components", row.getId(), null, row.getUpdatedAt())) {
                    continue;
                }
                nutritionDao.upsertComponent(new NutritionFoodComponentsRoomEntity(
                        row.getId(),
                        row.getOwnerId(),
                        row.getParentFoodId(),
                        row.getChildFoodId(),
                        row.getQuantity(),
                        emptyToDefault(row.getUnit(), "serving"),
                        row.getOrderIndex(),
                        emptyToDefault(row.getCreatedAt(), now()),
                        emptyToDefault(row.getUpdatedAt(), now()),
                        row.getDeletedAt()
                ));
                applied[0]++;
            }
        });
        return applied[0];
    }

    @Override
    public int applyRemoteProductLinkRows(List<NutritionProductLinkSyncRow> rows) {
        final int[] applied = {0};
        roomDatabase.runInTransaction(() -> {
            for (NutritionProductLinkSyncRow row : rows) {
                if (row == null || row.getId() == null || row.getOwnerId() == null
                        || row.getNutritionFoodId() == null || row.getCatalogProductId() == null
                        || !isKnownLinkStatus(row.getStatus())
                        || !isKnownLinkSource(row.getSourceType())) {
                    continue;
                }
                if (ProductNutritionLink.STATUS_APPROVED.equals(row.getStatus())
                        && row.getDeletedAt() == null) {
                    String localApprovedUpdatedAt = nutritionDao.otherApprovedLinkUpdatedAt(
                            row.getOwnerId(), row.getNutritionFoodId(), row.getId()
                    );
                    if (compareVersions(localApprovedUpdatedAt, row.getUpdatedAt()) > 0) {
                        continue;
                    }
                    nutritionDao.softDeleteApprovedLinks(
                            row.getOwnerId(),
                            row.getNutritionFoodId(),
                            row.getId(),
                            emptyToDefault(row.getUpdatedAt(), now())
                    );
                }
                if (!shouldApplyRemoteRoomRow(
                        "product_nutrition_links", row.getId(), row.getRevision(), row.getUpdatedAt())) {
                    continue;
                }
                nutritionDao.upsertProductLink(new ProductNutritionLinksRoomEntity(
                        row.getId(),
                        row.getOwnerId(),
                        row.getNutritionFoodId(),
                        row.getCatalogProductId(),
                        row.getStandardProductId(),
                        row.getStatus(),
                        row.getSourceType(),
                        row.getProposalReference(),
                        emptyToDefault(row.getProductContractVersion(), ProductReadV1.CONTRACT_VERSION),
                        row.getCatalogProductRevision(),
                        row.getCatalogContentAmount(),
                        row.getCatalogContentUnit(),
                        row.getCatalogPackageCount(),
                        row.getRevision(),
                        row.getReviewedAt(),
                        emptyToDefault(row.getCreatedAt(), now()),
                        emptyToDefault(row.getUpdatedAt(), now()),
                        row.getDeletedAt()
                ));
                applied[0]++;
            }
        });
        return applied[0];
    }

    private boolean shouldApplyRemoteRoomRow(
            String table,
            String id,
            Integer remoteRevision,
            String remoteUpdatedAt
    ) {
        NutritionRoomDao.SyncVersionRow local;
        if ("nutrition_foods".equals(table)) {
            local = nutritionDao.foodSyncVersion(id);
        } else if ("nutrition_food_nutrients".equals(table)) {
            local = nutritionDao.nutrientSyncVersion(id);
        } else if ("nutrition_food_components".equals(table)) {
            local = nutritionDao.componentSyncVersion(id);
        } else {
            local = nutritionDao.productLinkSyncVersion(id);
        }
        if (local == null) {
            return true;
        }
        if (remoteRevision != null && local.getRevision() != null) {
            if (remoteRevision > local.getRevision()) {
                return true;
            }
            if (remoteRevision < local.getRevision()) {
                return false;
            }
        }
        return compareVersions(remoteUpdatedAt, local.getUpdatedAt()) > 0;
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

    /**
     * Compatibility policy text used by the existing sync contract tests. Runtime reads use
     * the equivalent typed queries on NutritionRoomDao; this helper never executes SQL.
     */
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

    private static String requireName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    private static String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "local-user" : normalized;
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

}
