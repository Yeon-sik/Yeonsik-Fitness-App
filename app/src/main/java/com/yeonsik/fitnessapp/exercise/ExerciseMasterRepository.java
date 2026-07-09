package com.yeonsik.fitnessapp.exercise;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExerciseMasterRepository {
    private static final String INDEX_FILE_NAME = "Fitness_Index.json";
    private static final String WEIGHT_FILE_NAME = "Fitness_Weight.json";

    private final Context appContext;
    private ExerciseMasterCatalog cachedCatalog;

    public ExerciseMasterRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public synchronized List<WeightExercise> getAllWeightExercises() {
        return catalog().exercises;
    }

    public synchronized List<ExerciseCategory> getExerciseCategories() {
        return catalog().categories;
    }

    public synchronized List<WeightExercise> getExercisesByBodyPart(BodyPart bodyPart) {
        List<WeightExercise> results = new ArrayList<>();
        for (WeightExercise exercise : catalog().exercises) {
            if (exercise.bodyPart == bodyPart) {
                results.add(exercise);
            }
        }
        return results;
    }

    public synchronized List<WeightExercise> getExercisesByEquipment(EquipmentType equipmentType) {
        List<WeightExercise> results = new ArrayList<>();
        for (WeightExercise exercise : catalog().exercises) {
            if (exercise.equipmentType == equipmentType) {
                results.add(exercise);
            }
        }
        return results;
    }

    public synchronized List<WeightExercise> searchExercises(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return getAllWeightExercises();
        }

        List<WeightExercise> results = new ArrayList<>();
        for (WeightExercise exercise : catalog().exercises) {
            if (containsIgnoreCase(exercise.nameKo, normalized)
                    || containsIgnoreCase(exercise.nameEn, normalized)
                    || containsIgnoreCase(exercise.primarySubPartNameKo, normalized)
                    || containsIgnoreCase(exercise.equipmentNameKo, normalized)
                    || containsIgnoreCase(exercise.equipmentType.id(), normalized)
                    || containsIgnoreCase(exercise.bodyPart.labelKo(), normalized)) {
                results.add(exercise);
            }
        }
        return results;
    }

    public synchronized WeightExercise getExerciseById(String exerciseId) {
        if (exerciseId == null || exerciseId.trim().isEmpty()) {
            return null;
        }
        return catalog().exerciseById.get(exerciseId.trim());
    }

    public synchronized RoutineExercise toRoutineExercise(String exerciseId) {
        return ExerciseMasterAdapter.toRoutineExercise(getExerciseById(exerciseId));
    }

    private ExerciseMasterCatalog catalog() {
        if (cachedCatalog == null) {
            cachedCatalog = loadCatalog();
        }
        return cachedCatalog;
    }

    private ExerciseMasterCatalog loadCatalog() {
        try {
            JSONObject indexJson = new JSONObject(readAsset(INDEX_FILE_NAME));
            JSONObject weightJson = new JSONObject(readAsset(WEIGHT_FILE_NAME));

            List<ExerciseCategory> categories = parseCategories(indexJson.optJSONArray("uiParts"));
            List<WeightExercise> exercises = parseExercises(weightJson.optJSONArray("exercises"));
            Map<String, WeightExercise> byId = new LinkedHashMap<>();
            for (WeightExercise exercise : exercises) {
                byId.put(exercise.id, exercise);
            }

            return new ExerciseMasterCatalog(categories, exercises, byId);
        } catch (Exception ignored) {
            return ExerciseMasterCatalog.empty();
        }
    }

    private List<ExerciseCategory> parseCategories(JSONArray uiPartsJson) {
        List<ExerciseCategory> categories = new ArrayList<>();
        if (uiPartsJson == null) {
            return categories;
        }

        for (int index = 0; index < uiPartsJson.length(); index++) {
            JSONObject item = uiPartsJson.optJSONObject(index);
            if (item == null) {
                continue;
            }

            BodyPart bodyPart = BodyPart.fromId(item.optString("id"));
            if (bodyPart == null) {
                continue;
            }

            List<ExerciseCategory.SubPart> subParts = new ArrayList<>();
            JSONArray subPartsJson = item.optJSONArray("subParts");
            if (subPartsJson != null) {
                for (int subIndex = 0; subIndex < subPartsJson.length(); subIndex++) {
                    JSONObject subPart = subPartsJson.optJSONObject(subIndex);
                    if (subPart == null) {
                        continue;
                    }
                    subParts.add(new ExerciseCategory.SubPart(
                            subPart.optString("id"),
                            subPart.optString("nameKo")
                    ));
                }
            }

            categories.add(new ExerciseCategory(
                    bodyPart,
                    item.optString("nameKo", bodyPart.labelKo()),
                    item.optString("description"),
                    subParts
            ));
        }
        return categories;
    }

    private List<WeightExercise> parseExercises(JSONArray exercisesJson) {
        List<WeightExercise> exercises = new ArrayList<>();
        if (exercisesJson == null) {
            return exercises;
        }

        for (int index = 0; index < exercisesJson.length(); index++) {
            JSONObject item = exercisesJson.optJSONObject(index);
            if (item == null) {
                continue;
            }

            BodyPart bodyPart = BodyPart.fromId(item.optString("uiPart"));
            EquipmentType equipmentType = EquipmentType.fromId(item.optString("equipment"));
            if (bodyPart == null || equipmentType == null) {
                continue;
            }

            exercises.add(new WeightExercise(
                    item.optString("id"),
                    item.optString("nameKo"),
                    item.optString("nameEn"),
                    bodyPart,
                    item.optString("primarySubPart"),
                    item.optString("primarySubPartNameKo"),
                    toStringList(item.optJSONArray("secondarySubParts")),
                    toStringList(item.optJSONArray("secondarySubPartNamesKo")),
                    equipmentType,
                    item.optString("equipmentNameKo"),
                    item.optString("movementPattern"),
                    item.optString("movementPatternNameKo"),
                    item.optString("mechanicType"),
                    item.optString("mechanicTypeNameKo"),
                    item.optString("laterality"),
                    item.optString("lateralityNameKo"),
                    item.optString("resistanceType"),
                    item.optString("resistanceTypeNameKo"),
                    item.optString("recordType"),
                    item.optString("recordTypeNameKo"),
                    item.optString("motionType"),
                    item.optString("motionTypeNameKo"),
                    item.optString("notes")
            ));
        }
        return exercises;
    }

    private List<String> toStringList(JSONArray jsonArray) {
        List<String> values = new ArrayList<>();
        if (jsonArray == null) {
            return values;
        }

        for (int index = 0; index < jsonArray.length(); index++) {
            String value = jsonArray.optString(index);
            if (value != null && !value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String readAsset(String fileName) throws Exception {
        AssetManager assetManager = appContext.getAssets();
        try (InputStream inputStream = assetManager.open(fileName);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
