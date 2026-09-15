package com.yeonsik.fitnessapp.feature.meal.data;

import androidx.annotation.Nullable;

import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.core.database.MealRecordItemNutrientsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordItemComponentsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordItemComponentNutrientsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordItemConsumptionsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordItemsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRecordsRoomEntity;
import com.yeonsik.fitnessapp.core.database.MealRoomDao;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.DiningOutConsumption;
import com.yeonsik.fitnessapp.data.DiningOutFulfillmentMode;
import com.yeonsik.fitnessapp.data.DiningOutIdentity;
import com.yeonsik.fitnessapp.data.DiningOutOption;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.MealItemSnapshot;
import com.yeonsik.fitnessapp.data.MealMenuSelection;
import com.yeonsik.fitnessapp.data.MealRecordKind;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionTotals;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi;
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns account-scoped runtime meal-record writes for the meal feature. */
public final class MealRecordRepository implements MealRecordRepositoryApi {
    private static final String DEVICE_ID = "android-local";

    private final FitnessRoomDatabase roomDatabase;
    private final MealRoomDao mealDao;
    private final NutritionCatalogRepositoryApi nutritionCatalog;
    private String userId;

    public MealRecordRepository(
            FitnessRoomDatabase roomDatabase,
            NutritionCatalogRepositoryApi nutritionCatalog,
            String userId
    ) {
        this.roomDatabase = roomDatabase;
        this.mealDao = roomDatabase.mealRoomDao();
        this.nutritionCatalog = nutritionCatalog;
        setUserId(userId);
    }

    @Override
    public void setUserId(String userId) {
        String normalized = userId == null ? "" : userId.trim();
        this.userId = normalized.isEmpty() ? "local-user" : normalized;
    }

    @Override
    public String saveFoodMeal(AccountScope scope, String date, String mealTime, String foodId, double quantity) {
        String ownerId = requireActiveOwner(scope);
        NutritionFood food = nutritionCatalog.findFoodById(foodId);
        if (food == null) {
            throw new IllegalArgumentException("선택한 식품을 찾지 못했습니다.");
        }
        MealCompositionItem item = MealCompositionItem.from(food, quantity, food.basisUnit);
        MealItemSnapshot snapshot = MealItemSnapshot.of(item, 0);
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = now();
        String recordId = UUID.randomUUID().toString();
        int mealIndex = safeInt(mealDao.mealCountForDate(ownerId, recordDate.toString()));
        String mealLabel = MealEntryPolicy.labelForIndex(mealIndex);

        String metadata = foodMetadata(mealLabel, eatenAt);
        MealRecordsRoomEntity record = new MealRecordsRoomEntity(
                recordId,
                ownerId,
                recordDate.toString(),
                MealEntryPolicy.previewTitle(food.displayName(), 1, mealLabel + " 식사"),
                MealRecordKind.FOOD,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Math.round(item.calories),
                item.proteinGrams,
                item.carbsGrams,
                item.fatGrams,
                now,
                backfilled ? 1L : 0L,
                backfilled ? now : null,
                backfilled ? "manual past meal entry" : null,
                now,
                null,
                DEVICE_ID,
                "fitness",
                "fitness",
                metadata,
                1L
        );

        roomDatabase.runInTransaction(() -> {
            mealDao.insertRecord(record);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
        });
        return recordId;
    }

    public String saveManualDiningOut(
            AccountScope scope,
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            int calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            double sodiumMg,
            double sugarsGrams,
            double saturatedFatGrams
    ) {
        return saveManualDiningOut(
                scope, date, mealTime, storeName, branchName, menuName,
                calories, proteinGrams, carbsGrams, fatGrams, sodiumMg,
                sugarsGrams, saturatedFatGrams, null, null, null, null
        );
    }

    @Override
    public String saveManualDiningOut(
            AccountScope scope,
            String date,
            String mealTime,
            String storeName,
            String branchName,
            String menuName,
            int calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
    ) {
        String ownerId = requireActiveOwner(scope);
        String store = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String menu = MealEntryPolicy.requireDiningOutMenuName(menuName);
        MealEntryPolicy.requireDiningOutMenuNutrition(
                calories, proteinGrams, carbsGrams, fatGrams,
                sodiumMg, sugarsGrams, saturatedFatGrams
        );
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, (double) calories)
                .value(NutritionProfile.PROTEIN_GRAMS, proteinGrams)
                .value(NutritionProfile.CARBS_GRAMS, carbsGrams)
                .value(NutritionProfile.FAT_GRAMS, fatGrams)
                .value(NutritionProfile.SODIUM_MG, sodiumMg)
                .value(NutritionProfile.SUGARS_GRAMS, sugarsGrams)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, saturatedFatGrams)
                .build();
        NutritionFood food = NutritionFood.builder()
                .id(null)
                .ownerId(ownerId)
                .name(menu)
                .brand(store)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1d, NutritionUnit.SERVING)
                .prepState(NutritionFood.PREP_AS_SERVED)
                .profile(profile)
                .source("manual_estimate", "dining_out")
                .dataVersion(NutritionFood.DATA_VERSION_REQUIRED_SEVEN)
                .build();
        MealItemSnapshot snapshot = MealItemSnapshot.of(MealCompositionItem.from(food, 1d), 0);
        return insertDiningOutRecord(
                ownerId, date, mealTime, store, optional(branchName), menu,
                calories, proteinGrams, carbsGrams, fatGrams, snapshot,
                restaurantId, restaurantLocationId, restaurantMenuId, catalogProductId
        );
    }

    @Override
    public String saveComplexDiningOutMeal(
            AccountScope scope,
            String date,
            String mealTime,
            String storeName,
            String branchName,
            DiningOutIdentity identity,
            String fulfillmentMode,
            java.util.List<MealMenuSelection> menuSelections,
            double nominalServings,
            DiningOutConsumption consumption
    ) {
        String ownerId = requireActiveOwner(scope);
        if (consumption == null) {
            throw new IllegalArgumentException("공유 외식 섭취 정보가 필요합니다.");
        }
        List<MealMenuSelection> menus = menuSelections == null
                ? Collections.emptyList()
                : new ArrayList<>(menuSelections);
        if (menus.isEmpty()) {
            throw new IllegalArgumentException("외식 메뉴를 하나 이상 추가하세요.");
        }
        double normalizedNominalServings = requireNominalServings(nominalServings);
        String normalizedStore = MealEntryPolicy.requireDiningOutStoreName(storeName);
        String normalizedBranch = identity == null
                ? optional(branchName)
                : identity.branchName;
        String normalizedFulfillment = fulfillmentMode == null
                ? null
                : DiningOutFulfillmentMode.require(fulfillmentMode);

        NutritionTotals.Builder totalBuilder = NutritionTotals.builder();
        for (MealMenuSelection menu : menus) {
            if (menu == null || menu.menu == null) {
                throw new IllegalArgumentException("외식 메뉴에 빈 항목이 있습니다.");
            }
            totalBuilder.add(menu.menu.profile.scaled(consumption.consumedFraction));
            for (DiningOutOption option : menu.diningOutOptions) {
                totalBuilder.add(option.consumedProfile());
            }
        }
        NutritionTotals total = totalBuilder.build();
        Double calories = total.total(NutritionProfile.CALORIES_KCAL).completeValue();
        Double protein = total.total(NutritionProfile.PROTEIN_GRAMS).completeValue();
        Double carbs = total.total(NutritionProfile.CARBS_GRAMS).completeValue();
        Double fat = total.total(NutritionProfile.FAT_GRAMS).completeValue();
        if (calories == null || protein == null || carbs == null || fat == null) {
            throw new IllegalArgumentException(
                    "복합 외식 메뉴의 칼로리·탄수화물·단백질·지방은 모두 확인되어야 합니다."
            );
        }

        String templateId = compositionTemplateId(menus);
        Integer templateRevision = compositionTemplateRevision(menus);
        return insertComplexDiningOutRecord(
                ownerId,
                date,
                mealTime,
                normalizedStore,
                normalizedBranch,
                identity,
                normalizedFulfillment,
                menus,
                (int) Math.round(calories),
                protein,
                carbs,
                fat,
                templateId,
                templateRevision,
                normalizedNominalServings,
                consumption
        );
    }

    private String insertComplexDiningOutRecord(
            String ownerId,
            String date,
            String mealTime,
            String storeName,
            String branchName,
            DiningOutIdentity identity,
            String fulfillmentMode,
            List<MealMenuSelection> menus,
            int calories,
            double protein,
            double carbs,
            double fat,
            String templateId,
            Integer templateRevision,
            double nominalServings,
            DiningOutConsumption consumption
    ) {
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = now();
        String recordId = UUID.randomUUID().toString();
        int mealIndex = safeInt(mealDao.mealCountForDate(ownerId, recordDate.toString()));
        String menuName = menus.get(0).menu.food.name;
        List<DiningOutOption> options = diningOutOptions(menus);
        String metadata = complexDiningMetadata(
                mealIndex,
                eatenAt,
                storeName,
                branchName,
                menuName,
                identity,
                fulfillmentMode,
                menus.size(),
                options.size(),
                templateId,
                templateRevision,
                consumption
        );
        MealRecordsRoomEntity record = new MealRecordsRoomEntity(
                recordId,
                ownerId,
                recordDate.toString(),
                menuName,
                MealRecordKind.DINING_OUT,
                fulfillmentMode,
                storeName,
                branchName,
                menuName,
                identity == null ? null : identity.restaurantId,
                identity == null ? null : identity.restaurantLocationId,
                identity == null ? null : identity.restaurantMenuId,
                identity == null ? null : identity.catalogProductId,
                templateId,
                templateRevision == null ? null : templateRevision.longValue(),
                DiningOutConsumption.CONTRACT_VERSION,
                Math.round(calories),
                protein,
                carbs,
                fat,
                now,
                backfilled ? 1L : 0L,
                backfilled ? now : null,
                backfilled ? "manual past dining-out entry" : null,
                now,
                null,
                DEVICE_ID,
                "fitness",
                "fitness",
                metadata,
                1L
        );
        roomDatabase.runInTransaction(() -> {
            mealDao.insertRecord(record);
            List<String> menuItemIds = new ArrayList<>();
            for (int index = 0; index < menus.size(); index++) {
                MealMenuSelection menu = menus.get(index);
                if (menu == null) {
                    throw new IllegalArgumentException("외식 메뉴에 빈 항목이 있습니다.");
                }
                List<DiningOutOption> menuOptions = menu.diningOutOptions;
                String itemId = insertComplexMenuSnapshot(
                        recordId,
                        menu,
                        index,
                        now,
                        ownerId,
                        templateId,
                        templateRevision,
                        nominalServings,
                        menuOptions
                );
                menuItemIds.add(itemId);
            }
            for (String itemId : menuItemIds) {
                mealDao.insertConsumption(new MealRecordItemConsumptionsRoomEntity(
                        UUID.randomUUID().toString(),
                        ownerId,
                        recordId,
                        itemId,
                        DiningOutConsumption.CONTRACT_VERSION,
                        DiningOutConsumption.CONSUMER_SCOPE_SELF,
                        (long) consumption.dinerCount,
                        consumption.consumedFraction,
                        consumption.shareMethod,
                        consumption.confidence,
                        now,
                        now,
                        null,
                        DEVICE_ID
                ));
            }
        });
        return recordId;
    }

    private String insertDiningOutRecord(
            String ownerId,
            String date,
            String mealTime,
            String store,
            String branch,
            String menu,
            int calories,
            double protein,
            double carbs,
            double fat,
            MealItemSnapshot snapshot,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
    ) {
        LocalDate today = LocalDate.now();
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(date, today);
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        boolean backfilled = MealEntryPolicy.isBackfilled(recordDate, today);
        String now = now();
        String recordId = UUID.randomUUID().toString();
        int mealIndex = safeInt(mealDao.mealCountForDate(ownerId, recordDate.toString()));

        MealRecordsRoomEntity record = new MealRecordsRoomEntity(
                recordId,
                ownerId,
                recordDate.toString(),
                menu,
                MealRecordKind.DINING_OUT,
                null,
                store,
                branch,
                menu,
                optional(restaurantId),
                optional(restaurantLocationId),
                optional(restaurantMenuId),
                optional(catalogProductId),
                null,
                null,
                null,
                calories,
                protein,
                carbs,
                fat,
                now,
                backfilled ? 1L : 0L,
                backfilled ? now : null,
                backfilled ? "manual past dining-out entry" : null,
                now,
                null,
                DEVICE_ID,
                "fitness",
                "fitness",
                diningMetadata(
                        mealIndex,
                        eatenAt,
                        store,
                        branch,
                        menu,
                        restaurantId,
                        restaurantLocationId,
                        restaurantMenuId,
                        catalogProductId
                ),
                1L
        );

        roomDatabase.runInTransaction(() -> {
            mealDao.insertRecord(record);
            insertItemSnapshot(recordId, snapshot, now, ownerId);
        });
        return recordId;
    }

    private String insertComplexMenuSnapshot(
            String recordId,
            MealMenuSelection menu,
            int menuIndex,
            String now,
            String ownerId,
            String templateId,
            Integer templateRevision,
            double nominalServings,
            List<DiningOutOption> options
    ) {
        String itemId = insertItemSnapshot(
                recordId,
                MealItemSnapshot.of(menu.menu, menuIndex),
                now,
                ownerId,
                templateId,
                templateRevision,
                "whole_menu",
                nominalServings
        );
        for (int index = 0; index < menu.components.size(); index++) {
            DiningOutOption option = options == null || index >= options.size()
                    ? null
                    : options.get(index);
            MealItemSnapshot component = option == null
                    ? MealItemSnapshot.of(menu.components.get(index), index)
                    : MealItemSnapshot.of(
                            menu.components.get(index),
                            index,
                            option.groupKey,
                            option.groupType,
                            option.role,
                            option.memberId
                    );
            insertComponentSnapshot(
                    recordId,
                    itemId,
                    component,
                    now,
                    ownerId,
                    option == null ? null : option.consumedFraction,
                    option == null ? null : option.provisionType
            );
        }
        return itemId;
    }

    private void insertItemSnapshot(String recordId, MealItemSnapshot snapshot, String now, String ownerId) {
        insertItemSnapshot(recordId, snapshot, now, ownerId, null, null, null, null);
    }

    private String insertItemSnapshot(
            String recordId,
            MealItemSnapshot snapshot,
            String now,
            String ownerId,
            String templateId,
            Integer templateRevision,
            String portionBasis,
            Double nominalServings
    ) {
        String itemId = UUID.randomUUID().toString();
        Map<String, Double> typed = snapshot.typedNutritionColumns();
        MealRecordItemsRoomEntity item = new MealRecordItemsRoomEntity(
                itemId,
                ownerId,
                recordId,
                null,
                null,
                optional(snapshot.foodId),
                snapshot.foodNameSnapshot,
                snapshot.brandSnapshot,
                snapshot.manufacturerNameSnapshot,
                snapshot.brandNameSnapshot,
                snapshot.subBrandNameSnapshot,
                snapshot.productNameSnapshot,
                snapshot.packageAmountSnapshot,
                snapshot.packageUnitSnapshot,
                snapshot.packageCountSnapshot == null ? null : snapshot.packageCountSnapshot.longValue(),
                snapshot.foodKindSnapshot,
                snapshot.quantity,
                snapshot.unit,
                snapshot.basisAmountSnapshot,
                snapshot.basisUnitSnapshot,
                portionBasis,
                nominalServings,
                snapshot.prepStateSnapshot,
                required(typed, NutritionProfile.CALORIES_KCAL),
                required(typed, NutritionProfile.PROTEIN_GRAMS),
                required(typed, NutritionProfile.CARBS_GRAMS),
                required(typed, NutritionProfile.FAT_GRAMS),
                typed.get(NutritionProfile.SODIUM_MG),
                typed.get(NutritionProfile.SATURATED_FAT_GRAMS),
                typed.get(NutritionProfile.SUGARS_GRAMS),
                typed.get(NutritionProfile.FIBER_GRAMS),
                typed.get(NutritionProfile.ADDED_SUGARS_GRAMS),
                typed.get(NutritionProfile.TRANS_FAT_GRAMS),
                typed.get(NutritionProfile.CHOLESTEROL_MG),
                snapshot.sourceTypeSnapshot,
                snapshot.sourceReferenceSnapshot,
                snapshot.sourceVersionSnapshot,
                (long) snapshot.foodDataVersionSnapshot,
                (long) snapshot.orderIndex,
                now,
                now,
                null,
                DEVICE_ID
        );
        mealDao.insertItem(item);

        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            mealDao.insertNutrient(new MealRecordItemNutrientsRoomEntity(
                    UUID.randomUUID().toString(),
                    ownerId,
                    recordId,
                    itemId,
                    row.nutrientCode,
                    row.amount,
                    row.unit,
                    now,
                    now,
                    null,
                    DEVICE_ID
                ));
        }
        return itemId;
    }

    private void insertComponentSnapshot(
            String recordId,
            String itemId,
            MealItemSnapshot snapshot,
            String now,
            String ownerId,
            Double consumedFraction,
            String provisionType
    ) {
        Map<String, Double> typed = snapshot.typedNutritionColumns();
        String componentId = UUID.randomUUID().toString();
        mealDao.insertComponent(new MealRecordItemComponentsRoomEntity(
                componentId,
                ownerId,
                recordId,
                itemId,
                snapshot.compositionGroupKeySnapshot,
                snapshot.compositionGroupTypeSnapshot,
                provisionType,
                snapshot.compositionRoleSnapshot,
                snapshot.compositionMemberIdSnapshot,
                optional(snapshot.foodId),
                snapshot.foodNameSnapshot,
                snapshot.brandSnapshot,
                snapshot.foodKindSnapshot,
                snapshot.quantity,
                snapshot.unit,
                snapshot.basisAmountSnapshot,
                snapshot.basisUnitSnapshot,
                snapshot.prepStateSnapshot,
                consumedFraction,
                typed.get(NutritionProfile.CALORIES_KCAL),
                typed.get(NutritionProfile.PROTEIN_GRAMS),
                typed.get(NutritionProfile.CARBS_GRAMS),
                typed.get(NutritionProfile.FAT_GRAMS),
                typed.get(NutritionProfile.SODIUM_MG),
                typed.get(NutritionProfile.SATURATED_FAT_GRAMS),
                typed.get(NutritionProfile.SUGARS_GRAMS),
                typed.get(NutritionProfile.FIBER_GRAMS),
                typed.get(NutritionProfile.ADDED_SUGARS_GRAMS),
                typed.get(NutritionProfile.TRANS_FAT_GRAMS),
                typed.get(NutritionProfile.CHOLESTEROL_MG),
                snapshot.sourceTypeSnapshot,
                snapshot.sourceReferenceSnapshot,
                snapshot.sourceVersionSnapshot,
                (long) snapshot.foodDataVersionSnapshot,
                (long) snapshot.orderIndex,
                now,
                now,
                null,
                DEVICE_ID
        ));
        for (MealItemSnapshot.MicronutrientRow row : snapshot.micronutrientRows()) {
            mealDao.insertComponentNutrient(new MealRecordItemComponentNutrientsRoomEntity(
                    UUID.randomUUID().toString(),
                    ownerId,
                    recordId,
                    itemId,
                    componentId,
                    row.nutrientCode,
                    row.amount,
                    row.unit,
                    now,
                    now,
                    null,
                    DEVICE_ID
            ));
        }
    }

    private List<DiningOutOption> diningOutOptions(List<MealMenuSelection> menus) {
        List<DiningOutOption> options = new ArrayList<>();
        for (MealMenuSelection menu : menus) {
            if (menu != null) {
                options.addAll(menu.diningOutOptions);
            }
        }
        return options;
    }

    private String compositionTemplateId(List<MealMenuSelection> menus) {
        for (DiningOutOption option : diningOutOptions(menus)) {
            if (option == null || option.sourceReference == null) {
                continue;
            }
            try {
                String id = new JSONObject(option.sourceReference)
                        .optString("composition_template_id", "")
                        .trim();
                if (!id.isEmpty()) {
                    return id;
                }
            } catch (Exception ignored) {
                // Legacy source references may not contain a template identity.
            }
        }
        return null;
    }

    private Integer compositionTemplateRevision(List<MealMenuSelection> menus) {
        for (DiningOutOption option : diningOutOptions(menus)) {
            if (option == null || option.sourceReference == null) {
                continue;
            }
            try {
                JSONObject source = new JSONObject(option.sourceReference);
                if (!source.has("composition_template_revision")
                        || source.isNull("composition_template_revision")) {
                    continue;
                }
                int revision = source.optInt("composition_template_revision", 0);
                return revision <= 0 ? null : revision;
            } catch (Exception ignored) {
                // Legacy source references may not contain a template revision.
            }
        }
        return null;
    }

    private String complexDiningMetadata(
            int mealIndex,
            String eatenAt,
            String storeName,
            String branchName,
            String menuName,
            DiningOutIdentity identity,
            String fulfillmentMode,
            int menuCount,
            int optionCount,
            String templateId,
            Integer templateRevision,
            DiningOutConsumption consumption
    ) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("item_type", "meal");
            metadata.put("meal_kind", MealRecordKind.DINING_OUT);
            metadata.put("meal_type", MealEntryPolicy.labelForIndex(mealIndex));
            metadata.put("eaten_at", eatenAt);
            metadata.put("store_name", storeName);
            putOptional(metadata, "branch_name", branchName);
            metadata.put("menu_name", menuName);
            metadata.put("nutrition_status", "estimated");
            metadata.put("nutrition_source", "catalog_snapshot");
            metadata.put("estimated", true);
            metadata.put("composition_version", "3");
            metadata.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
            metadata.put("composition_kind", templateId == null ? "ad_hoc_selection" : "template");
            metadata.put("item_count", menuCount);
            metadata.put("option_count", optionCount);
            metadata.put("diner_count", consumption.dinerCount);
            metadata.put("consumed_fraction", consumption.consumedFraction);
            metadata.put("share_method", consumption.shareMethod);
            metadata.put("confidence", consumption.confidence);
            putOptional(metadata, "fulfillment_mode", fulfillmentMode);
            putOptional(metadata, "composition_template_id", templateId);
            if (templateRevision == null) {
                metadata.put("composition_template_revision", JSONObject.NULL);
            } else {
                metadata.put("composition_template_revision", templateRevision);
            }
            if (identity != null) {
                metadata.put("identity_contract", DiningOutIdentity.CONTRACT_VERSION);
                metadata.put("identity_namespace", DiningOutIdentity.NAMESPACE);
                putOptional(metadata, "location_source_namespace", identity.locationSourceNamespace);
                metadata.put("restaurant_id", identity.restaurantId);
                metadata.put("restaurant_name", identity.restaurantName);
                metadata.put("restaurant_location_id", identity.restaurantLocationId);
                putOptional(metadata, "source_location_code", identity.sourceLocationCode);
                putOptional(metadata, "restaurant_menu_id", identity.restaurantMenuId);
                putOptional(metadata, "catalog_product_id", identity.catalogProductId);
            }
            return metadata.toString();
        } catch (Exception error) {
            throw new IllegalStateException("복합 외식 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private static double requireNominalServings(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0d || value > 100d) {
            throw new IllegalArgumentException("메뉴 제공량은 0보다 크고 100 이하이어야 합니다.");
        }
        return value;
    }

    @Override
    public boolean updateMealTime(AccountScope scope, String recordId, String mealTime) {
        String ownerId = requireActiveOwner(scope);
        String normalizedId = optional(recordId);
        if (normalizedId == null) {
            return false;
        }
        MealRoomDao.EditableMealRecord existing = mealDao.editableMealRecord(
                normalizedId,
                ownerId,
                DEVICE_ID
        );
        if (existing == null) {
            return false;
        }
        LocalDate recordDate = MealEntryPolicy.requireRecordDate(existing.getDate(), LocalDate.now());
        String eatenAt = MealEntryPolicy.eatenAt(recordDate, mealTime, ZoneId.systemDefault());
        String metadata = metadataWithEatenAt(existing.getMetadata(), eatenAt);
        return mealDao.updateMealMetadata(
                normalizedId,
                ownerId,
                DEVICE_ID,
                metadata,
                now()
        ) > 0;
    }

    @Override
    public boolean deleteMeal(AccountScope scope, String recordId) {
        String ownerId = requireActiveOwner(scope);
        String normalizedId = optional(recordId);
        if (normalizedId == null) {
            return false;
        }
        String timestamp = now();
        final int[] deleted = new int[]{0};
        roomDatabase.runInTransaction(() -> {
            deleted[0] = mealDao.tombstoneMealRecord(
                    normalizedId, ownerId, timestamp, timestamp
            );
            mealDao.tombstoneMealItems(normalizedId, ownerId, timestamp, timestamp);
            mealDao.tombstoneMealNutrients(normalizedId, ownerId, timestamp, timestamp);
            mealDao.tombstoneMealComponents(normalizedId, ownerId, timestamp, timestamp);
            mealDao.tombstoneMealConsumptions(normalizedId, ownerId, timestamp, timestamp);
            mealDao.tombstoneMealComponentNutrients(normalizedId, ownerId, timestamp, timestamp);
        });
        return deleted[0] > 0;
    }

    private static String metadataWithEatenAt(String metadata, String eatenAt) {
        try {
            JSONObject values = new JSONObject(metadata == null ? "{}" : metadata);
            values.put("eaten_at", eatenAt);
            return values.toString();
        } catch (Exception error) {
            throw new IllegalStateException("끼니 메타데이터를 수정하지 못했습니다.", error);
        }
    }

    private String foodMetadata(String mealLabel, String eatenAt) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("item_type", "meal");
            metadata.put("meal_kind", MealRecordKind.FOOD);
            metadata.put("meal_type", mealLabel);
            metadata.put("eaten_at", eatenAt);
            metadata.put("estimated", "false");
            metadata.put("composition_version", "2");
            metadata.put("item_count", "1");
            return metadata.toString();
        } catch (Exception error) {
            throw new IllegalStateException("식단 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private String diningMetadata(
            int mealIndex,
            String eatenAt,
            String store,
            String branch,
            String menu,
            String restaurantId,
            String restaurantLocationId,
            String restaurantMenuId,
            String catalogProductId
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("item_type", "meal");
            json.put("meal_kind", MealRecordKind.DINING_OUT);
            json.put("meal_type", MealEntryPolicy.labelForIndex(mealIndex));
            json.put("eaten_at", eatenAt);
            json.put("store_name", store);
            if (branch != null) json.put("branch_name", branch);
            json.put("menu_name", menu);
            json.put("nutrition_status", "estimated");
            json.put("nutrition_source", "manual_estimate");
            json.put("estimated", "true");
            putOptional(json, "restaurant_id", restaurantId);
            putOptional(json, "restaurant_location_id", restaurantLocationId);
            putOptional(json, "restaurant_menu_id", restaurantMenuId);
            putOptional(json, "catalog_product_id", catalogProductId);
            json.put("composition_version", "3");
            json.put("composition_contract", CompositionTemplate.CONTRACT_VERSION);
            json.put("composition_kind", "standalone");
            json.put("item_count", "1");
            json.put("option_count", "0");
            return json.toString();
        } catch (Exception error) {
            throw new IllegalStateException("외식 메타데이터를 만들지 못했습니다.", error);
        }
    }

    private String requireActiveOwner(AccountScope scope) {
        String requested = scope == null ? "" : scope.getOwnerId().trim();
        if (requested.isEmpty() || !requested.equals(userId)) {
            throw new IllegalStateException("계정이 전환되어 식사 저장을 취소했습니다.");
        }
        return requested;
    }

    private static Double required(Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("필수 영양소가 없습니다: " + key);
        }
        return value;
    }

    private static String now() {
        return DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static String optional(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void putOptional(JSONObject values, String key, String value) throws Exception {
        String normalized = optional(value);
        if (normalized != null) values.put(key, normalized);
    }
}
