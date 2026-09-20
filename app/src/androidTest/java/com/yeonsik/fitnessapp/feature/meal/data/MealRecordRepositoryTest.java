package com.yeonsik.fitnessapp.feature.meal.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitness.shared.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.DiningOutConsumption;
import com.yeonsik.fitnessapp.data.DiningOutFulfillmentMode;
import com.yeonsik.fitnessapp.data.DiningOutOption;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealMenuSelection;
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.test.FitnessRoomTestDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MealRecordRepositoryTest {
    private static final String DATABASE_PREFIX = "meal_feature_repository_";
    private static final String OWNER = "meal-owner-a";

    @Test
    public void foodMealWritesAnOwnerScopedImmutableSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood(
                    "검증 식품",
                    NutritionFood.KIND_INGREDIENT,
                    100d,
                    NutritionUnit.GRAM,
                    NutritionFood.PREP_AS_SERVED,
                    requiredProfile(),
                    "test",
                    null,
                    "v1"
            );
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromLegacy(helper);
            MealRecordRepository repository = new MealRecordRepository(
                    FitnessRoomDatabaseProvider.get(context), catalog, OWNER
            );

            String recordId = repository.saveFoodMeal(
                    new AccountScope(OWNER),
                    LocalDate.now().toString(),
                    "12:30",
                    food.id,
                    150d
            );

            try (Cursor record = database.rawQuery(
                    "SELECT user_id, meal_kind, calories FROM meal_records WHERE id = ?",
                    new String[]{recordId}
            )) {
                assertEquals(true, record.moveToFirst());
                assertEquals(OWNER, record.getString(0));
                assertEquals("food", record.getString(1));
                assertEquals(300, record.getInt(2));
            }
            try (Cursor item = database.rawQuery(
                    "SELECT user_id, food_id, quantity, sodium_mg FROM meal_record_items "
                            + "WHERE meal_record_id = ?",
                    new String[]{recordId}
            )) {
                assertEquals(true, item.moveToFirst());
                assertEquals(OWNER, item.getString(0));
                assertEquals(food.id, item.getString(1));
                assertEquals(150d, item.getDouble(2), 0.001d);
                assertEquals(750d, item.getDouble(3), 0.001d);
            }
        } finally {
            room.close();
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void staleAccountScopeCannotWriteAfterAccountSwitch() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood(
                    "격리 식품",
                    NutritionFood.KIND_INGREDIENT,
                    1d,
                    NutritionUnit.SERVING,
                    NutritionFood.PREP_AS_SERVED,
                    requiredProfile(),
                    "test",
                    null,
                    "v1"
            );
            MealRecordRepository repository = new MealRecordRepository(
                    FitnessRoomDatabaseProvider.get(context), catalog, OWNER);
            repository.setUserId("meal-owner-b");

            assertThrows(IllegalStateException.class, () -> repository.saveFoodMeal(
                    new AccountScope(OWNER), LocalDate.now().toString(), "18:00", food.id, 1d));
        } finally {
            room.close();
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void complexDiningOutMealCopiesMenusComponentsServingAndConsumptionSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood menu = catalog.saveFood(
                    "세트 메뉴",
                    NutritionFood.KIND_INGREDIENT,
                    1d,
                    NutritionUnit.SERVING,
                    NutritionFood.PREP_AS_SERVED,
                    requiredProfile(),
                    "test",
                    "catalog://set",
                    "v1"
            );
            MealMenuSelection selection = MealMenuSelection.diningOut(
                    MealCompositionItem.from(menu, 1d),
                    OWNER,
                    "테스트 식당",
                    Collections.singletonList(DiningOutOption.grouped(
                            "사이드",
                            NutritionProfile.ofMacros(50d, 2d, 5d, 1d),
                            null,
                            "{\"composition_template_id\":\"template-test\","
                                    + "\"composition_template_revision\":3}",
                            "side",
                            "side",
                            "사이드",
                            "optional",
                            "member-side",
                            "paid",
                            0.5d
                    ))
            );
            MealRecordRepository repository = new MealRecordRepository(
                    FitnessRoomDatabaseProvider.get(context), catalog, OWNER
            );
            String recordId = repository.saveComplexDiningOutMeal(
                    new AccountScope(OWNER),
                    LocalDate.now().minusDays(1).toString(),
                    "12:30",
                    "테스트 식당",
                    "강남점",
                    null,
                    DiningOutFulfillmentMode.DINE_IN.value(),
                    Collections.singletonList(selection),
                    2d,
                    DiningOutConsumption.manual(2, 0.5d)
            );

            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromLegacy(helper);
            try (Cursor record = database.rawQuery(
                    "SELECT composition_template_id, composition_template_revision, "
                            + "fulfillment_mode, metadata FROM meal_records WHERE id = ?",
                    new String[]{recordId}
            )) {
                assertTrue(record.moveToFirst());
                assertEquals("template-test", record.getString(0));
                assertEquals(3, record.getInt(1));
                assertEquals("dine_in", record.getString(2));
                assertTrue(record.getString(3).contains("\"consumed_fraction\":0.5"));
            }
            try (Cursor item = database.rawQuery(
                    "SELECT portion_basis_snapshot, nominal_servings_snapshot, food_name_snapshot "
                            + "FROM meal_record_items WHERE meal_record_id = ?",
                    new String[]{recordId}
            )) {
                assertTrue(item.moveToFirst());
                assertEquals("whole_menu", item.getString(0));
                assertEquals(2d, item.getDouble(1), 0.001d);
                assertEquals("세트 메뉴", item.getString(2));
            }
            try (Cursor component = database.rawQuery(
                    "SELECT composition_group_key_snapshot, provision_type_snapshot, "
                            + "consumed_fraction, sodium_mg FROM meal_record_item_components "
                            + "WHERE meal_record_id = ?",
                    new String[]{recordId}
            )) {
                assertTrue(component.moveToFirst());
                assertEquals("side", component.getString(0));
                assertEquals("paid", component.getString(1));
                assertEquals(0.5d, component.getDouble(2), 0.001d);
                assertTrue(component.isNull(3));
            }
            try (Cursor consumption = database.rawQuery(
                    "SELECT diner_count, consumed_fraction, share_method FROM "
                            + "meal_record_item_consumptions WHERE meal_record_id = ?",
                    new String[]{recordId}
            )) {
                assertTrue(consumption.moveToFirst());
                assertEquals(2, consumption.getInt(0));
                assertEquals(0.5d, consumption.getDouble(1), 0.001d);
                assertEquals("manual", consumption.getString(2));
            }

            database.execSQL(
                    "UPDATE nutrition_foods SET name = '변경된 카탈로그', calories_kcal = 9999 "
                            + "WHERE id = ?",
                    new Object[]{menu.id}
            );
            com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead reloaded =
                    new MealReadRepository(FitnessRoomDatabaseProvider.get(context))
                            .mealSnapshot(new AccountScope(OWNER), recordId);
            assertNotNull(reloaded);
            assertEquals("세트 메뉴", reloaded.getItems().get(0).getFoodName());
            assertEquals("사이드", reloaded.getItems().get(0).getComponents().get(0).getFoodName());
            assertEquals(0.5d, reloaded.getItems().get(0).getConsumption().getConsumedFraction(), 0.001d);
            try (Cursor snapshot = database.rawQuery(
                    "SELECT food_name_snapshot, calories FROM meal_record_items "
                            + "WHERE meal_record_id = ?",
                    new String[]{recordId}
            )) {
                assertTrue(snapshot.moveToFirst());
                assertEquals("세트 메뉴", snapshot.getString(0));
                assertEquals(200d, snapshot.getDouble(1), 0.001d);
            }
        } finally {
            room.close();
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void historicalMealCanBeRetimedAndDeletedWithoutChangingAnotherDate() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood(
                    "기간 검증 식품",
                    NutritionFood.KIND_INGREDIENT,
                    1d,
                    NutritionUnit.SERVING,
                    NutritionFood.PREP_AS_SERVED,
                    requiredProfile(),
                    "test",
                    null,
                    "v1"
            );
            MealRecordRepository repository = new MealRecordRepository(
                    FitnessRoomDatabaseProvider.get(context), catalog, OWNER);
            MealReadRepository readRepository = new MealReadRepository(
                    FitnessRoomDatabaseProvider.get(context));
            AccountScope scope = new AccountScope(OWNER);
            String today = LocalDate.now().toString();
            String yesterday = LocalDate.now().minusDays(1).toString();

            String yesterdayId = repository.saveFoodMeal(
                    scope, yesterday, "08:00", food.id, 1d);
            String todayId = repository.saveFoodMeal(
                    scope, today, "12:00", food.id, 1d);

            assertEquals(1, readRepository.mealCount(scope, yesterday));
            assertEquals(1, readRepository.mealCount(scope, today));
            assertTrue(repository.updateMealTime(scope, yesterdayId, "18:40"));
            assertEquals("18:40", readRepository.meals(scope, yesterday).get(0).getMealTime());
            assertEquals("12:00", readRepository.meals(scope, today).get(0).getMealTime());

            assertThrows(IllegalStateException.class, () ->
                    repository.deleteMeal(new AccountScope("another-owner"), yesterdayId));
            assertTrue(repository.deleteMeal(scope, yesterdayId));
            assertEquals(0, readRepository.mealCount(scope, yesterday));
            assertEquals(1, readRepository.mealCount(scope, today));

            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromLegacy(helper);
            try (Cursor record = database.rawQuery(
                    "SELECT deleted_at FROM meal_records WHERE id = ?",
                    new String[]{yesterdayId}
            )) {
                assertTrue(record.moveToFirst());
                assertNotNull(record.getString(0));
            }
            try (Cursor item = database.rawQuery(
                    "SELECT deleted_at FROM meal_record_items WHERE meal_record_id = ?",
                    new String[]{yesterdayId}
            )) {
                assertTrue(item.moveToFirst());
                assertNotNull(item.getString(0));
            }
            assertNotNull(todayId);
        } finally {
            room.close();
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionCatalogRepository catalog(
            FitnessRoomDatabase room,
            Context context,
            String owner
    ) {
        return new NutritionCatalogRepository(room, context, owner);
    }

    private static NutritionProfile requiredProfile() {
        return NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 200d)
                .value(NutritionProfile.PROTEIN_GRAMS, 20d)
                .value(NutritionProfile.CARBS_GRAMS, 25d)
                .value(NutritionProfile.FAT_GRAMS, 4d)
                .value(NutritionProfile.SODIUM_MG, 500d)
                .value(NutritionProfile.SUGARS_GRAMS, 3d)
                .value(NutritionProfile.SATURATED_FAT_GRAMS, 1d)
                .build();
    }

    private static IsolatedDatabaseContext isolatedContext() {
        IsolatedDatabaseContext context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext());
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        return context;
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private IsolatedDatabaseContext(Context base) {
            super(base);
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(DATABASE_PREFIX + name);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory
        ) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(
                String name,
                int mode,
                SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler
        ) {
            return SQLiteDatabase.openDatabase(
                    getDatabasePath(name).getPath(),
                    factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY,
                    errorHandler
            );
        }
    }
}
