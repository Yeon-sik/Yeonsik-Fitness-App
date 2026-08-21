package com.yeonsik.fitnessapp.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Local CRUD boundary for reusable composition definitions. */
public final class CompositionTemplateRepository {
    private static final String DEVICE_ID = "android-local";

    private final FitnessDatabaseHelper dbHelper;
    private String userId;

    public CompositionTemplateRepository(FitnessDatabaseHelper dbHelper, String userId) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("Database helper is required.");
        }
        this.dbHelper = dbHelper;
        this.userId = normalizeUserId(userId);
    }

    public void setUserId(String userId) {
        this.userId = normalizeUserId(userId);
    }

    public String save(CompositionTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Composition template is required.");
        }
        if (!userId.equals(template.userId)) {
            throw new IllegalArgumentException("Composition template belongs to another user.");
        }
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        String now = OffsetDateTime.now().toString();
        database.beginTransaction();
        try {
            database.delete(
                    "composition_members",
                    "user_id = ? AND template_id = ?",
                    new String[]{userId, template.id}
            );
            database.delete(
                    "composition_groups",
                    "user_id = ? AND template_id = ?",
                    new String[]{userId, template.id}
            );

            ContentValues templateValues = new ContentValues();
            templateValues.put("id", template.id);
            templateValues.put("user_id", userId);
            templateValues.put("name", template.name);
            templateValues.put("template_kind", template.kind);
            if (template.rootFoodId == null) {
                templateValues.putNull("root_food_id");
            } else {
                templateValues.put("root_food_id", template.rootFoodId);
            }
            if (template.sourceReference == null) {
                templateValues.putNull("source_reference");
            } else {
                templateValues.put("source_reference", template.sourceReference);
            }
            templateValues.put("revision", template.revision);
            templateValues.put("created_at", now);
            templateValues.put("updated_at", now);
            templateValues.putNull("deleted_at");
            templateValues.put("device_id", DEVICE_ID);
            database.insertWithOnConflict(
                    "composition_templates",
                    null,
                    templateValues,
                    SQLiteDatabase.CONFLICT_REPLACE
            );

            for (CompositionGroup group : template.groups) {
                ContentValues groupValues = new ContentValues();
                groupValues.put("id", group.id);
                groupValues.put("user_id", userId);
                groupValues.put("template_id", template.id);
                groupValues.put("group_key", group.key);
                groupValues.put("label", group.label);
                groupValues.put("selection_mode", group.selectionMode);
                groupValues.put("min_selected", group.minSelected);
                groupValues.put("max_selected", group.maxSelected);
                groupValues.put("order_index", group.orderIndex);
                groupValues.put("created_at", now);
                groupValues.put("updated_at", now);
                groupValues.putNull("deleted_at");
                groupValues.put("device_id", DEVICE_ID);
                database.insertOrThrow("composition_groups", null, groupValues);

                for (CompositionMember member : group.members) {
                    insertMember(database, template, group.id, member, now);
                }
            }
            database.setTransactionSuccessful();
            return template.id;
        } finally {
            database.endTransaction();
        }
    }

    public CompositionTemplate find(String templateId) {
        String id = normalizeNullable(templateId);
        if (id == null) {
            return null;
        }
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        try (Cursor cursor = database.query(
                "composition_templates",
                new String[]{
                        "id", "user_id", "name", "template_kind", "root_food_id",
                        "source_reference", "revision"
                },
                "id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{id, userId},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readTemplate(database, cursor);
        }
    }

    public List<CompositionTemplate> list(String kind) {
        List<CompositionTemplate> templates = new ArrayList<>();
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String normalizedKind = normalizeNullable(kind);
        String selection = "user_id = ? AND deleted_at IS NULL";
        List<String> arguments = new ArrayList<>();
        arguments.add(userId);
        if (normalizedKind != null) {
            selection += " AND template_kind = ?";
            arguments.add(normalizedKind);
        }
        try (Cursor cursor = database.query(
                "composition_templates",
                new String[]{
                        "id", "user_id", "name", "template_kind", "root_food_id",
                        "source_reference", "revision"
                },
                selection,
                arguments.toArray(new String[0]),
                null,
                null,
                "updated_at DESC, id ASC"
        )) {
            while (cursor.moveToNext()) {
                templates.add(readTemplate(database, cursor));
            }
        }
        return Collections.unmodifiableList(templates);
    }

    public void delete(String templateId) {
        String id = normalizeNullable(templateId);
        if (id == null) {
            return;
        }
        SQLiteDatabase database = dbHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(
                    "composition_members",
                    "user_id = ? AND template_id = ?",
                    new String[]{userId, id}
            );
            database.delete(
                    "composition_groups",
                    "user_id = ? AND template_id = ?",
                    new String[]{userId, id}
            );
            database.delete(
                    "composition_templates",
                    "user_id = ? AND id = ?",
                    new String[]{userId, id}
            );
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    private CompositionTemplate readTemplate(SQLiteDatabase database, Cursor cursor) {
        String templateId = cursor.getString(0);
        List<CompositionGroup> groups = new ArrayList<>();
        try (Cursor groupCursor = database.query(
                "composition_groups",
                new String[]{
                        "id", "group_key", "label", "selection_mode", "min_selected",
                        "max_selected", "order_index"
                },
                "template_id = ? AND user_id = ? AND deleted_at IS NULL",
                new String[]{templateId, userId},
                null,
                null,
                "order_index ASC, id ASC"
        )) {
            while (groupCursor.moveToNext()) {
                String groupId = groupCursor.getString(0);
                List<CompositionMember> members = new ArrayList<>();
                try (Cursor memberCursor = database.query(
                        "composition_members",
                        new String[]{
                                "id", "nutrition_food_id", "name_snapshot", "brand_snapshot",
                                "quantity", "unit", "default_selected", "order_index",
                                "source_reference_snapshot"
                        },
                        "group_id = ? AND user_id = ? AND deleted_at IS NULL",
                        new String[]{groupId, userId},
                        null,
                        null,
                        "order_index ASC, id ASC"
                )) {
                    while (memberCursor.moveToNext()) {
                        String nutritionFoodId = nullable(memberCursor, 1);
                        members.add(new CompositionMember(
                                memberCursor.getString(0),
                                nutritionFoodId,
                                memberCursor.getString(2),
                                nullable(memberCursor, 3),
                                memberCursor.getDouble(4),
                                memberCursor.getString(5),
                                memberCursor.getInt(6) != 0,
                                memberCursor.getInt(7),
                                nullable(memberCursor, 8),
                                readFoodProfile(database, nutritionFoodId)
                        ));
                    }
                }
                groups.add(new CompositionGroup(
                        groupId,
                        groupCursor.getString(1),
                        groupCursor.getString(2),
                        groupCursor.getString(3),
                        groupCursor.getInt(4),
                        groupCursor.getInt(5),
                        groupCursor.getInt(6),
                        members
                ));
            }
        }
        return new CompositionTemplate(
                templateId,
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                nullable(cursor, 4),
                nullable(cursor, 5),
                cursor.getInt(6),
                groups
        );
    }

    private void insertMember(
            SQLiteDatabase database,
            CompositionTemplate template,
            String groupId,
            CompositionMember member,
            String now
    ) {
        ContentValues values = new ContentValues();
        values.put("id", member.id);
        values.put("user_id", userId);
        values.put("template_id", template.id);
        values.put("group_id", groupId);
        if (member.nutritionFoodId == null) {
            values.putNull("nutrition_food_id");
        } else {
            values.put("nutrition_food_id", member.nutritionFoodId);
        }
        values.put("name_snapshot", member.name);
        if (member.brand == null) {
            values.putNull("brand_snapshot");
        } else {
            values.put("brand_snapshot", member.brand);
        }
        values.put("quantity", member.quantity);
        values.put("unit", member.unit);
        values.put("default_selected", member.defaultSelected ? 1 : 0);
        values.put("order_index", member.orderIndex);
        if (member.sourceReference == null) {
            values.putNull("source_reference_snapshot");
        } else {
            values.put("source_reference_snapshot", member.sourceReference);
        }
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("deleted_at");
        values.put("device_id", DEVICE_ID);
        database.insertOrThrow("composition_members", null, values);
    }

    private NutritionProfile readFoodProfile(SQLiteDatabase database, String foodId) {
        if (foodId == null) {
            return NutritionProfile.empty();
        }
        try (Cursor cursor = database.query(
                "nutrition_foods",
                new String[]{
                        "calories_kcal", "protein_grams", "carbs_grams", "fat_grams",
                        "sodium_mg", "saturated_fat_grams", "sugars_grams", "fiber_grams",
                        "added_sugars_grams", "trans_fat_grams", "cholesterol_mg"
                },
                "id = ? AND (deleted_at IS NULL)",
                new String[]{foodId},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return NutritionProfile.empty();
            }
            NutritionProfile.Builder builder = NutritionProfile.builder();
            String[] keys = {
                    NutritionProfile.CALORIES_KCAL,
                    NutritionProfile.PROTEIN_GRAMS,
                    NutritionProfile.CARBS_GRAMS,
                    NutritionProfile.FAT_GRAMS,
                    NutritionProfile.SODIUM_MG,
                    NutritionProfile.SATURATED_FAT_GRAMS,
                    NutritionProfile.SUGARS_GRAMS,
                    NutritionProfile.FIBER_GRAMS,
                    NutritionProfile.ADDED_SUGARS_GRAMS,
                    NutritionProfile.TRANS_FAT_GRAMS,
                    NutritionProfile.CHOLESTEROL_MG
            };
            for (int i = 0; i < keys.length; i++) {
                builder.value(keys[i], cursor.isNull(i) ? null : cursor.getDouble(i));
            }
            return builder.build();
        }
    }

    private String normalizeUserId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "local-user" : normalized;
    }

    private String normalizeNullable(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String nullable(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
