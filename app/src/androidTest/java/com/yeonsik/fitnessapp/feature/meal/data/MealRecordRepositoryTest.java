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
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.DiningOutConsumption;
import com.yeonsik.fitnessapp.data.DiningOutFulfillmentMode;
import com.yeonsik.fitnessapp.data.DiningOutOption;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealEntryPolicy;
import com.yeonsik.fitnessapp.data.MealMenuSelection;
import com.yeonsik.fitnessapp.feature.nutrition.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;
import com.yeonsik.fitnessapp.feature.meal.model.FoodPortionInput;
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMealInput;
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMenuIntake;
import com.yeonsik.fitnessapp.test.FitnessRoomTestDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public final class MealRecordRepositoryTest {
    private static final String DATABASE_PREFIX = "meal_feature_repository_";
    private static final String OWNER = "meal-owner-a";

    @Test
    public void foodMealWritesAnOwnerScopedImmutableSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
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
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            MealRecordRepository repository = new MealRecordRepository(
                    room, catalog, OWNER
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
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void staleAccountScopeCannotWriteAfterAccountSwitch() {
        IsolatedDatabaseContext context = isolatedContext();
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
                    room, catalog, OWNER);
            repository.setUserId("meal-owner-b");

            assertThrows(IllegalStateException.class, () -> repository.saveFoodMeal(
                    new AccountScope(OWNER), LocalDate.now().toString(), "18:00", food.id, 1d));
        } finally {
            room.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void complexDiningOutMealCopiesMenusComponentsServingAndConsumptionSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
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
                    room, catalog, OWNER
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

            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
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
                    new MealReadRepository(room)
                            .mealSnapshot(new AccountScope(OWNER), recordId);
            assertNotNull(reloaded);
            assertEquals("세트 메뉴", reloaded.getItems().get(0).getFoodName());
            assertEquals("사이드", reloaded.getItems().get(0).getComponents().get(0).getFoodName());
            assertEquals(0.5d, reloaded.getItems().get(0).getConsumption().getConsumedFraction(), 0.001d);
            List<List<String>> children = snapshotRows(database, recordId);
            List<List<String>> provenance = queryRows(database,
                    "SELECT created_at,is_backfilled,backfilled_at,backfill_reason FROM meal_records WHERE id=?", recordId);
            assertTrue(repository.updateMealDateTime(new AccountScope(OWNER), recordId,
                    LocalDate.now().minusDays(3).toString(), "19:20"));
            assertEquals(children, snapshotRows(database, recordId));
            assertEquals(provenance, queryRows(database,
                    "SELECT created_at,is_backfilled,backfilled_at,backfill_reason FROM meal_records WHERE id=?", recordId));
            assertTotal(new MealReadRepository(room).mealTotals(new AccountScope(OWNER), LocalDate.now().minusDays(3).toString()),
                    "sugars_grams", 1.5d, 1, 1);
            assertTotal(new MealReadRepository(room).mealTotals(new AccountScope(OWNER), LocalDate.now().minusDays(3).toString()),
                    "saturated_fat_grams", 0.5d, 1, 1);
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
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void historicalMealCanBeRetimedAndDeletedWithoutChangingAnotherDate() {
        IsolatedDatabaseContext context = isolatedContext();
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
                    room, catalog, OWNER);
            MealReadRepository readRepository = new MealReadRepository(
                    room);
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

            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
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
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void multipleFoodsCreateOneMealWithOrderedSnapshots() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, "nutrition-owner");
            NutritionFood first = catalog.saveFood("밥", NutritionFood.KIND_INGREDIENT, 100d, NutritionUnit.GRAM,
                    NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            NutritionFood second = catalog.saveFood("닭", NutritionFood.KIND_INGREDIENT, 100d, NutritionUnit.GRAM,
                    NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            AccountScope scope = new AccountScope(OWNER);
            String date = LocalDate.now().toString();
            String id = repository.saveFoodComposition(scope, date, "19:00", Arrays.asList(
                    new FoodPortionInput(first.id, 150d), new FoodPortionInput(second.id, 50d)));
            MealReadRepository read = new MealReadRepository(room);
            assertEquals(1, read.mealCount(scope, date));
            assertEquals(400, read.meals(scope, date).get(0).getCalories());
            assertEquals(2, read.mealSnapshot(scope, id).getItems().size());
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            try (Cursor rows = database.rawQuery(
                    "SELECT food_id,quantity,sodium_mg,user_id FROM meal_record_items WHERE meal_record_id=? ORDER BY order_index",
                    new String[]{id})) {
                assertTrue(rows.moveToFirst());
                assertEquals(first.id, rows.getString(0));
                assertEquals(150d, rows.getDouble(1), 0.001d);
                assertEquals(750d, rows.getDouble(2), 0.001d);
                assertEquals(OWNER, rows.getString(3));
                assertTrue(rows.moveToNext());
                assertEquals(second.id, rows.getString(0));
                assertEquals(50d, rows.getDouble(1), 0.001d);
            }
            assertThrows(IllegalArgumentException.class, () -> repository.saveFoodComposition(scope, date, "20:00",
                    Arrays.asList(new FoodPortionInput(first.id, 100d), new FoodPortionInput("missing-food", 100d))));
            assertEquals(1, read.mealCount(scope, date));
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void registrationOrderContinuesBeyondFiveAndSurvivesTimeChanges() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood("순서 식품", NutritionFood.KIND_INGREDIENT, 100d, NutritionUnit.GRAM,
                    NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            MealReadRepository read = new MealReadRepository(room);
            AccountScope scope = new AccountScope(OWNER);
            String date = LocalDate.now().toString();
            List<String> ids = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                ids.add(repository.saveFoodMeal(scope, date, (18 - index) + ":00", food.id, 100d));
            }
            for (int index = 0; index < 6; index++) {
                assertEquals(ids.get(index), read.meals(scope, date).get(index).getId());
                assertEquals((index + 1) + "끼", read.meals(scope, date).get(index).getMealLabel());
            }
            assertTrue(repository.updateMealTime(scope, ids.get(0), "23:00"));
            assertEquals(ids.get(0), read.meals(scope, date).get(0).getId());
            repository.saveFoodMeal(scope, date, "07:00", food.id, 100d);
            assertEquals("7끼", read.meals(scope, date).get(6).getMealLabel());
            assertEquals(0, read.mealCount(scope, LocalDate.now().minusDays(1).toString()));
        } finally {
            room.close(); helper.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void diningPortionPersistsItsQuantityAndUnknownExtendedNutrition() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            String id = repository.saveDiningOutPortion(new AccountScope(OWNER), LocalDate.now().toString(), "13:00",
                    new DiningOutMealInput("식당", "", "메뉴", 0.5d, 600, 40d, 80d, 20d,
                            null, null, null, null, null, null, null, null));
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            try (Cursor item = database.rawQuery(
                    "SELECT quantity,calories,protein_grams,sodium_mg,trans_fat_grams FROM meal_record_items WHERE meal_record_id=?",
                    new String[]{id})) {
                assertTrue(item.moveToFirst());
                assertEquals(0.5d, item.getDouble(0), 0.001d);
                assertEquals(300d, item.getDouble(1), 0.001d);
                assertEquals(20d, item.getDouble(2), 0.001d);
                assertTrue(item.isNull(3));
                assertTrue(item.isNull(4));
            }
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void independentMenuFractionsKeepFullSnapshotsAndPersistOneConsumedMeal() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            AccountScope scope = new AccountScope(OWNER);
            String date = LocalDate.now().toString();
            DiningOutMealInput rice = new DiningOutMealInput("식당", "본점", "볶음밥", 1d, 600, 40d, 80d, 20d,
                    null, 8d, 4d, null, null, null, null, 0.4d);
            DiningOutMealInput dumplings = new DiningOutMealInput("식당", "본점", "만두", 2d, 200, 10d, 30d, 5d,
                    400d, 3d, 1d, null, null, null, null, null);
            String id = repository.saveDiningOutMenuItems(scope, date, "13:00", Arrays.asList(
                    new DiningOutMenuIntake(rice, 0.5d, null, 600.8d), new DiningOutMenuIntake(dumplings, 0.25d, null, 200.8d)));
            MealReadRepository read = new MealReadRepository(room);
            assertEquals(1, read.mealCount(scope, date));
            assertEquals(401, read.meals(scope, date).get(0).getCalories());
            assertEquals(25d, read.mealSnapshot(scope, id).getNutrition().getProteinGrams(), 0.001d);
            assertTotal(read.mealTotals(scope, date), "sugars_grams", 5.5d, 1, 0);
            assertTotal(read.mealTotals(scope, date), "saturated_fat_grams", 2.5d, 1, 0);
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            try (Cursor rows = database.rawQuery("SELECT i.food_name_snapshot,i.quantity,i.calories,c.consumed_fraction " +
                    "FROM meal_record_items i JOIN meal_record_item_consumptions c ON c.meal_record_item_id=i.id " +
                    "WHERE i.meal_record_id=? ORDER BY i.order_index", new String[]{id})) {
                assertTrue(rows.moveToFirst()); assertEquals("볶음밥", rows.getString(0));
                assertEquals(1d, rows.getDouble(1), 0.001d); assertEquals(600.8d, rows.getDouble(2), 0.001d);
                assertEquals(0.5d, rows.getDouble(3), 0.001d);
                assertTrue(rows.moveToNext()); assertEquals("만두", rows.getString(0));
                assertEquals(2d, rows.getDouble(1), 0.001d); assertEquals(401.6d, rows.getDouble(2), 0.001d);
                assertEquals(0.25d, rows.getDouble(3), 0.001d);
                assertEquals(2, rows.getCount());
            }
            assertEquals(0.5d, read.mealSnapshot(scope, id).getItems().get(0).getConsumption().getConsumedFraction(), 0.001d);
            assertEquals(0.25d, read.mealSnapshot(scope, id).getItems().get(1).getConsumption().getConsumedFraction(), 0.001d);
            try (Cursor rows = database.rawQuery("SELECT trans_fat_grams FROM meal_record_items " +
                    "WHERE meal_record_id=? ORDER BY order_index", new String[]{id})) {
                assertTrue(rows.moveToFirst()); assertEquals(0.4d, rows.getDouble(0), 0.001d);
                assertTrue(rows.moveToNext()); assertTrue(rows.isNull(0));
            }
            assertThrows(IllegalArgumentException.class, () -> repository.saveDiningOutMenuItems(scope, date, "14:00",
                    Arrays.asList(new DiningOutMenuIntake(rice, 0.5d, null), new DiningOutMenuIntake(dumplings, 1.01d, null))));
            assertEquals(1, read.mealCount(scope, date));
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void dateTimeEditMovesTheSameRecordAndPreservesMetadataAndAllSnapshots() throws Exception {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood("날짜 이동 식품", NutritionFood.KIND_INGREDIENT,
                    100d, NutritionUnit.GRAM, NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            MealReadRepository read = new MealReadRepository(room);
            AccountScope scope = new AccountScope(OWNER);
            LocalDate today = LocalDate.now();
            String id = repository.saveFoodMeal(scope, today.toString(), "12:30", food.id, 150d);
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            JSONObject metadata = new JSONObject(read.mealSnapshot(scope, id).getMetadata());
            metadata.put("unrelated", new JSONObject().put("keep", "unchanged"));
            database.execSQL("UPDATE meal_records SET metadata=?, updated_at='2000-01-01T00:00:00Z' WHERE id=?",
                    new Object[]{metadata.toString(), id});
            List<List<String>> snapshots = snapshotRows(database, id);
            List<List<String>> provenance = queryRows(database,
                    "SELECT id,created_at,is_backfilled,backfilled_at,backfill_reason FROM meal_records WHERE id=?", id);

            LocalDate previous = today;
            for (LocalDate target : Arrays.asList(today, today.minusDays(1), today.minusDays(3), today)) {
                assertTrue(repository.updateMealDateTime(scope, id, target.toString(), "18:40"));
                assertEquals(id, read.meals(scope, target.toString()).get(0).getId());
                if (!previous.equals(target)) assertEquals(0, read.mealCount(scope, previous.toString()));
                assertEquals(1, read.mealCount(scope, target.toString()));
                assertEquals(snapshots, snapshotRows(database, id));
                assertEquals(provenance, queryRows(database,
                        "SELECT id,created_at,is_backfilled,backfilled_at,backfill_reason FROM meal_records WHERE id=?", id));
                JSONObject updated = new JSONObject(read.mealSnapshot(scope, id).getMetadata());
                assertEquals("unchanged", updated.getJSONObject("unrelated").getString("keep"));
                assertEquals(MealEntryPolicy.eatenAt(target, "18:40", ZoneId.systemDefault()), updated.getString("eaten_at"));
                assertFalse("2000-01-01T00:00:00Z".equals(room.mealRoomDao().visibleMealRecord(id, OWNER).getUpdatedAt()));
                previous = target;
            }
            assertTrue(repository.updateMealTime(scope, id, "09:15"));
            assertEquals(today.toString(), read.mealSnapshot(scope, id).getDate());
            assertEquals("09:15", read.meals(scope, today.toString()).get(0).getMealTime());
            assertEquals(snapshots, snapshotRows(database, id));
            assertEquals(provenance, queryRows(database,
                    "SELECT id,created_at,is_backfilled,backfilled_at,backfill_reason FROM meal_records WHERE id=?", id));
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void dateTimeEditRejectsInvalidInputOtherOwnersDeletedAndRemoteRecords() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood food = catalog.saveFood("검증 식품", NutritionFood.KIND_INGREDIENT,
                    100d, NutritionUnit.GRAM, NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            MealReadRepository read = new MealReadRepository(room);
            AccountScope scope = new AccountScope(OWNER);
            String today = LocalDate.now().toString();
            String id = repository.saveFoodMeal(scope, today, "12:30", food.id, 100d);
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            List<List<String>> before = queryRows(database, "SELECT * FROM meal_records WHERE id=?", id);
            assertThrows(IllegalArgumentException.class, () -> repository.updateMealDateTime(scope, id,
                    LocalDate.now().plusDays(1).toString(), "12:00"));
            assertThrows(IllegalArgumentException.class, () -> repository.updateMealDateTime(scope, id, "2026-02-30", "12:00"));
            assertThrows(IllegalArgumentException.class, () -> repository.updateMealDateTime(scope, id, "not-a-date", "12:00"));
            assertThrows(IllegalArgumentException.class, () -> repository.updateMealDateTime(scope, id, today, "25:00"));
            assertThrows(IllegalStateException.class, () -> repository.updateMealDateTime(new AccountScope("other-owner"), id, today, "12:00"));
            repository.setUserId("other-owner");
            assertFalse(repository.updateMealDateTime(new AccountScope("other-owner"), id, today, "12:00"));
            assertNull(read.mealSnapshot(new AccountScope("other-owner"), id));
            repository.setUserId(OWNER);
            assertEquals(before, queryRows(database, "SELECT * FROM meal_records WHERE id=?", id));
            database.execSQL("UPDATE meal_records SET device_id='remote-import' WHERE id=?", new Object[]{id});
            assertFalse(repository.updateMealDateTime(scope, id, today, "12:00"));
            database.execSQL("UPDATE meal_records SET device_id='android-local' WHERE id=?", new Object[]{id});
            assertTrue(repository.deleteMeal(scope, id));
            List<List<String>> tombstone = queryRows(database, "SELECT * FROM meal_records WHERE id=?", id);
            assertFalse(repository.updateMealDateTime(scope, id, today, "12:00"));
            assertFalse(repository.updateMealTime(scope, id, "12:00"));
            assertNull(read.mealSnapshot(scope, id));
            assertEquals(tombstone, queryRows(database, "SELECT * FROM meal_records WHERE id=?", id));
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void snapshotTotalsKeepKnownZeroMissingAndPartialChildrenWithoutAddingToParentMacros() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessRoomDatabase room = FitnessRoomTestDatabase.open(context);
        try {
            NutritionCatalogRepository catalog = catalog(room, context, OWNER);
            NutritionFood known = catalog.saveFood("확인 식품", NutritionFood.KIND_INGREDIENT,
                    100d, NutritionUnit.GRAM, NutritionFood.PREP_AS_SERVED, requiredProfile(), "test", null, "v1");
            NutritionFood missing = catalog.saveFood("미확인 식품", NutritionFood.KIND_INGREDIENT,
                    100d, NutritionUnit.GRAM, NutritionFood.PREP_AS_SERVED,
                    NutritionProfile.ofMacros(200d, 20d, 25d, 4d), "test", null, "v1");
            NutritionFood zero = catalog.saveFood("0 확인 식품", NutritionFood.KIND_INGREDIENT,
                    100d, NutritionUnit.GRAM, NutritionFood.PREP_AS_SERVED,
                    NutritionProfile.builder().from(NutritionProfile.ofMacros(200d, 20d, 25d, 4d))
                            .value(NutritionProfile.SUGARS_GRAMS, 0d).value(NutritionProfile.SATURATED_FAT_GRAMS, 0d).build(),
                    "test", null, "v1");
            MealRecordRepository repository = new MealRecordRepository(room, catalog, OWNER);
            MealReadRepository read = new MealReadRepository(room);
            AccountScope scope = new AccountScope(OWNER);
            String date = LocalDate.now().toString();
            repository.saveFoodMealItems(scope, date, "12:00", Arrays.asList(
                    MealCompositionItem.from(known, 100d), MealCompositionItem.from(missing, 100d)));
            com.yeonsik.fitness.shared.feature.meal.model.MealReadNutritionTotals totals = read.mealTotals(scope, date);
            assertEquals(400d, totals.getTotals().get("calories_kcal").getSum(), 0.001d);
            assertEquals(40d, totals.getTotals().get("protein_grams").getSum(), 0.001d);
            assertEquals(50d, totals.getTotals().get("carbs_grams").getSum(), 0.001d);
            assertEquals(8d, totals.getTotals().get("fat_grams").getSum(), 0.001d);
            assertTotal(totals, "sugars_grams", 3d, 1, 1);
            assertTotal(totals, "saturated_fat_grams", 1d, 1, 1);
            String zeroDate = LocalDate.now().minusDays(1).toString();
            repository.saveFoodMeal(scope, zeroDate, "12:00", zero.id, 100d);
            assertTotal(read.mealTotals(scope, zeroDate), "sugars_grams", 0d, 1, 0);
            assertTotal(read.mealTotals(scope, zeroDate), "saturated_fat_grams", 0d, 1, 0);
            String legacyDate = LocalDate.now().minusDays(2).toString();
            String legacyId = repository.saveFoodMeal(scope, legacyDate, "12:00", known.id, 100d);
            FitnessDatabaseConnection database = FitnessDatabaseConnection.fromRoom(room);
            database.execSQL("DELETE FROM meal_record_items WHERE meal_record_id=?", new Object[]{legacyId});
            assertTrue(read.mealSnapshot(scope, legacyId).getItems().isEmpty());
            assertTotal(read.mealTotals(scope, legacyDate), "sugars_grams", 0d, 0, 1);
            assertTotal(read.mealTotals(scope, legacyDate), "saturated_fat_grams", 0d, 0, 1);
            database.execSQL("UPDATE nutrition_foods SET sugars_grams=999,saturated_fat_grams=999 WHERE id=?", new Object[]{known.id});
            assertTotal(read.mealTotals(scope, date), "sugars_grams", 3d, 1, 1);
            assertTotal(read.mealTotals(scope, date), "saturated_fat_grams", 1d, 1, 1);
        } finally {
            room.close(); context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static void assertTotal(com.yeonsik.fitness.shared.feature.meal.model.MealReadNutritionTotals totals,
            String key, double sum, int known, int missing) {
        com.yeonsik.fitness.shared.feature.meal.model.MealReadNutritionTotal total = totals.getTotals().get(key);
        assertNotNull(total); assertEquals(sum, total.getSum(), 0.001d);
        assertEquals(known, total.getKnown()); assertEquals(missing, total.getMissing());
    }

    private static List<List<String>> snapshotRows(FitnessDatabaseConnection database, String recordId) {
        List<List<String>> rows = new ArrayList<>();
        for (String table : Arrays.asList("meal_record_items", "meal_record_item_nutrients",
                "meal_record_item_components", "meal_record_item_consumptions", "meal_record_item_component_nutrients")) {
            rows.addAll(queryRows(database, "SELECT * FROM " + table + " WHERE meal_record_id=? ORDER BY id", recordId));
        }
        return rows;
    }

    private static List<List<String>> queryRows(FitnessDatabaseConnection database, String sql, String recordId) {
        List<List<String>> rows = new ArrayList<>();
        try (Cursor cursor = database.rawQuery(sql, new String[]{recordId})) {
            while (cursor.moveToNext()) {
                List<String> row = new ArrayList<>();
                for (int column = 0; column < cursor.getColumnCount(); column++) {
                    row.add(cursor.isNull(column) ? null : cursor.getString(column));
                }
                rows.add(row);
            }
        }
        return rows;
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
