package com.yeonsik.fitnessapp.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class CompositionTemplateRepositoryTest {
    private static final String USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String DATABASE_PREFIX = "composition_template_";

    @Test
    public void savesTemplateAndLinksResolvedDiningOutSnapshot() {
        Context context = new IsolatedDatabaseContext(
                ApplicationProvider.getApplicationContext()
        );
        context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        FitnessDatabaseHelper helper = new FitnessDatabaseHelper(context);
        try {
            FitnessRepository repository = new FitnessRepository(helper, USER_ID);
            CompositionMember drink = new CompositionMember(
                    "member-drink",
                    null,
                    "제로 콜라",
                    "맥도날드",
                    1,
                    NutritionUnit.SERVING,
                    false,
                    0,
                    null,
                    NutritionProfile.ofMacros(0, 0, 0, 0)
            );
            CompositionTemplate template = new CompositionTemplate(
                    "template-burger-set",
                    USER_ID,
                    "고추크림치즈버거 세트",
                    CompositionTemplate.KIND_DINING_OUT,
                    null,
                    "{\"schema_version\":\"composition-template.v1\"}",
                    1,
                    Collections.singletonList(new CompositionGroup(
                            "group-drink",
                            "drink",
                            "음료",
                            CompositionGroup.MODE_EXACTLY_ONE,
                            1,
                            1,
                            0,
                            Collections.singletonList(drink)
                    ))
            );
            repository.compositionTemplates().save(template);
            CompositionTemplate loaded = repository.compositionTemplates().find(template.id);
            assertNotNull(loaded);
            assertEquals("drink", loaded.groups.get(0).key);
            assertEquals(CompositionGroupType.BEVERAGE.value(), loaded.groups.get(0).groupType);
            assertEquals("member-drink", loaded.groups.get(0).members.get(0).id);

            DiningOutOption selected = DiningOutOption.grouped(
                    "제로 콜라",
                    NutritionProfile.ofMacros(0, 0, 0, 0),
                    null,
                    "{\"composition_template_id\":\"template-burger-set\","
                            + "\"composition_template_revision\":1}",
                    "drink",
                    "음료",
                    DiningOutOption.DEFAULT_ROLE,
                    "member-drink"
            );
            String recordId = repository.addDiningOutMealAtTimeWithOptionNutrition(
                    LocalDate.now().minusDays(1).toString(),
                    "12:30",
                    "맥도날드",
                    "고추크림치즈버거 세트",
                    20d,
                    10d,
                    5d,
                    null,
                    Collections.singletonList(selected)
            );
            SQLiteDatabase database = helper.getReadableDatabase();
            assertEquals("template-burger-set", scalar(database,
                    "SELECT composition_template_id FROM meal_records WHERE id = '" +
                            recordId + "'"));
            assertEquals("template-burger-set", scalar(database,
                    "SELECT composition_template_id FROM meal_record_items WHERE meal_record_id = '" +
                            recordId + "'"));
            assertEquals("drink", scalar(database,
                    "SELECT composition_group_key_snapshot FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "'"));
            assertEquals("beverage", scalar(database,
                    "SELECT composition_group_type_snapshot FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "'"));
            assertEquals("optional", scalar(database,
                    "SELECT composition_role_snapshot FROM meal_record_item_components " +
                            "WHERE meal_record_id = '" + recordId + "'"));
        } finally {
            helper.close();
            context.deleteDatabase(FitnessDatabaseHelper.DATABASE_NAME);
        }
    }

    private static String scalar(SQLiteDatabase database, String sql) {
        try (Cursor cursor = database.rawQuery(sql, null)) {
            if (!cursor.moveToFirst()) {
                throw new AssertionError("Expected one row for: " + sql);
            }
            return cursor.getString(0);
        }
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

        @Override
        public boolean deleteDatabase(String name) {
            return SQLiteDatabase.deleteDatabase(getDatabasePath(name));
        }
    }
}
