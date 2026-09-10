package com.yeonsik.fitnessapp.feature.meal.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.core.account.AccountScope;
import com.yeonsik.fitnessapp.core.database.FitnessDatabaseConnection;
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider;
import com.yeonsik.fitnessapp.data.FitnessDatabaseHelper;
import com.yeonsik.fitnessapp.data.NutritionCatalogRepository;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public final class MealRecordRepositoryTest {
    private static final String DATABASE_PREFIX = "meal_feature_repository_";
    private static final String OWNER = "meal-owner-a";

    @Test
    public void foodMealWritesAnOwnerScopedImmutableSnapshot() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = catalog(helper, OWNER);
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
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    @Test
    public void staleAccountScopeCannotWriteAfterAccountSwitch() {
        IsolatedDatabaseContext context = isolatedContext();
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            NutritionCatalogRepository catalog = catalog(helper, OWNER);
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
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static NutritionCatalogRepository catalog(FitnessDatabaseHelper helper, String owner) {
        return new NutritionCatalogRepository(helper, owner, SupabaseConfig.empty());
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
