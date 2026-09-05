package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.LoadState;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versioned JSON contract for moving workout summaries between FitnessApp installations. */
public final class WorkoutTransferCodec {
    public static final String FORMAT = "yeonsik.workout-transfer";
    public static final int V1 = 1;
    public static final int V2 = 2;

    private WorkoutTransferCodec() {
    }

    public static String encode(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("운동 전송 문서가 없습니다.");
        }
        validateDocument(document);
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("formatVersion", document.formatVersion);
            root.put("sourceApp", document.sourceApp);
            putNullable(root, "exportedAt", document.exportedAt);
            JSONArray sessions = new JSONArray();
            for (Session session : document.sessions) {
                sessions.put(encodeSession(session, document.formatVersion));
            }
            root.put("sessions", sessions);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalArgumentException("운동 전송 JSON을 만들지 못했습니다.", error);
        }
    }

    public static Document decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("운동 전송 JSON이 비어 있습니다.");
        }
        try {
            JSONObject root = new JSONObject(json);
            if (!FORMAT.equals(requiredString(root, "format"))) {
                throw new IllegalArgumentException("지원하지 않는 운동 전송 format입니다.");
            }
            int version = root.has("formatVersion")
                    ? requiredInt(root, "formatVersion")
                    : root.optInt("version", 0);
            if (version != V1 && version != V2) {
                throw new IllegalArgumentException("지원하지 않는 운동 전송 버전입니다: " + version);
            }
            String sourceApp = requiredString(root, "sourceApp");
            JSONArray sessionArray = root.optJSONArray("sessions");
            if (sessionArray == null) {
                sessionArray = root.optJSONArray("workouts");
            }
            if (sessionArray == null) {
                throw new IllegalArgumentException("운동 전송 sessions가 없습니다.");
            }
            List<Session> sessions = new ArrayList<>();
            for (int index = 0; index < sessionArray.length(); index += 1) {
                JSONObject item = sessionArray.optJSONObject(index);
                if (item == null) {
                    throw new IllegalArgumentException("운동 전송 세션 형식이 올바르지 않습니다.");
                }
                sessions.add(decodeSession(item, sourceApp, version));
            }
            Document document = new Document(
                    version,
                    sourceApp,
                    optionalString(root, "exportedAt"),
                    sessions
            );
            validateDocument(document);
            return document;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("운동 전송 JSON을 읽지 못했습니다.", error);
        }
    }

    private static JSONObject encodeSession(Session session, int version) throws Exception {
        JSONObject object = new JSONObject();
        object.put("sourceApp", session.sourceApp);
        object.put("sourceRecordId", session.sourceRecordId);
        putNullable(object, "date", session.date);
        putNullable(object, "title", session.title);
        putNullable(object, "workoutType", session.workoutType);
        putNullable(object, "category", session.category);
        putNullable(object, "durationSeconds", session.durationSeconds);
        putNullable(object, "status", session.status);
        putNullable(object, "startedAt", session.startedAt);
        putNullable(object, "endedAt", session.endedAt);
        JSONArray exercises = new JSONArray();
        for (Exercise exercise : session.exercises) {
            exercises.put(encodeExercise(exercise, version));
        }
        object.put("exercises", exercises);
        return object;
    }

    private static JSONObject encodeExercise(Exercise exercise, int version) throws Exception {
        JSONObject object = new JSONObject();
        object.put("exerciseId", exercise.exerciseId);
        putNullable(object, "canonicalExerciseId", exercise.canonicalExerciseId);
        putNullable(object, "canonicalPresetId", exercise.canonicalPresetId);
        putNullable(object, "exerciseName", exercise.exerciseName);
        object.put("orderIndex", exercise.orderIndex);
        putNullable(object, "recordType", exercise.recordType);
        putNullable(object, "uiPart", exercise.uiPart);
        putNullable(object, "equipment", exercise.equipment);
        JSONArray sets = new JSONArray();
        for (SetData set : exercise.sets) {
            sets.put(encodeSet(set, version));
        }
        object.put("sets", sets);
        return object;
    }

    private static JSONObject encodeSet(SetData set, int version) throws Exception {
        JSONObject object = new JSONObject();
        object.put("setIndex", set.setIndex);
        putNullable(object, "targetReps", set.targetReps);
        putNullable(object, "actualReps", set.actualReps);
        putNullable(object, "weightKg", set.weightKg);
        putNullable(object, "volumeKg", set.volumeKg);
        putNullable(object, "durationSeconds", set.durationSeconds);
        putNullable(object, "distanceMeters", set.distanceMeters);
        putNullable(object, "restSeconds", set.restSeconds);
        putNullable(object, "assistedWeightKg", set.assistedWeightKg);
        putNullable(object, "addedWeightKg", set.addedWeightKg);
        putNullable(object, "loadState", set.loadState);
        object.put("isCompleted", set.isCompleted);
        putNullable(object, "rpe", set.rpe);
        putNullable(object, "rir", set.rir);
        putNullable(object, "memo", set.memo);
        if (version >= V2) {
            putNullable(object, "inputLoadValue", set.inputLoadValue);
            putNullable(object, "inputLoadUnit", set.inputLoadUnit);
        }
        return object;
    }

    private static Session decodeSession(JSONObject object, String rootSourceApp, int version) {
        String sourceApp = optionalString(object, "sourceApp");
        if (sourceApp == null) {
            sourceApp = rootSourceApp;
        }
        String sourceRecordId = optionalString(object, "sourceRecordId");
        if (sourceRecordId == null) {
            sourceRecordId = optionalString(object, "recordId");
        }
        if (sourceRecordId == null) {
            throw new IllegalArgumentException("운동 전송 세션의 sourceRecordId가 없습니다.");
        }
        JSONArray exerciseArray = object.optJSONArray("exercises");
        if (exerciseArray == null) {
            exerciseArray = object.optJSONArray("workoutExercises");
        }
        if (exerciseArray == null) {
            throw new IllegalArgumentException("운동 전송 세션의 exercises가 없습니다.");
        }
        List<Exercise> exercises = new ArrayList<>();
        for (int index = 0; index < exerciseArray.length(); index += 1) {
            JSONObject item = exerciseArray.optJSONObject(index);
            if (item == null) {
                throw new IllegalArgumentException("운동 전송 종목 형식이 올바르지 않습니다.");
            }
            exercises.add(decodeExercise(item, version));
        }
        return new Session(
                sourceApp,
                sourceRecordId,
                optionalString(object, "date"),
                optionalString(object, "title", "exerciseName"),
                optionalString(object, "workoutType"),
                optionalString(object, "category"),
                optionalInteger(object, "durationSeconds"),
                optionalString(object, "status"),
                optionalString(object, "startedAt"),
                optionalString(object, "endedAt"),
                exercises
        );
    }

    private static Exercise decodeExercise(JSONObject object, int version) {
        String exerciseId = optionalString(object, "exerciseId");
        if (exerciseId == null) {
            exerciseId = optionalString(object, "id");
        }
        if (exerciseId == null) {
            throw new IllegalArgumentException("운동 전송 종목의 exerciseId가 없습니다.");
        }
        JSONArray setArray = object.optJSONArray("sets");
        if (setArray == null) {
            throw new IllegalArgumentException("운동 전송 종목의 sets가 없습니다.");
        }
        List<SetData> sets = new ArrayList<>();
        for (int index = 0; index < setArray.length(); index += 1) {
            JSONObject item = setArray.optJSONObject(index);
            if (item == null) {
                throw new IllegalArgumentException("운동 전송 세트 형식이 올바르지 않습니다.");
            }
            sets.add(decodeSet(item, version));
        }
        return new Exercise(
                exerciseId,
                optionalString(object, "canonicalExerciseId"),
                optionalString(object, "canonicalPresetId"),
                optionalString(object, "exerciseName", "name"),
                optionalInteger(object, "orderIndex", "order"),
                optionalString(object, "recordType"),
                optionalString(object, "uiPart"),
                optionalString(object, "equipment"),
                sets
        );
    }

    private static SetData decodeSet(JSONObject object, int version) {
        Double inputLoadValue = version >= V2
                ? optionalDouble(object, "inputLoadValue")
                : null;
        String inputLoadUnit = version >= V2
                ? optionalString(object, "inputLoadUnit")
                : null;
        return new SetData(
                requiredInt(object, "setIndex", "index"),
                optionalInteger(object, "targetReps"),
                optionalInteger(object, "actualReps", "reps"),
                optionalDouble(object, "weightKg"),
                optionalDouble(object, "volumeKg"),
                optionalInteger(object, "durationSeconds"),
                optionalDouble(object, "distanceMeters"),
                optionalInteger(object, "restSeconds"),
                optionalDouble(object, "assistedWeightKg"),
                optionalDouble(object, "addedWeightKg"),
                optionalString(object, "loadState"),
                object.has("isCompleted")
                        ? requiredBoolean(object, "isCompleted")
                        : object.optBoolean("completed", false),
                optionalInteger(object, "rpe"),
                optionalInteger(object, "rir"),
                optionalString(object, "memo"),
                inputLoadValue,
                inputLoadUnit
        );
    }

    public static void validate(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("운동 전송 문서가 없습니다.");
        }
        validateDocument(document);
    }

    private static void validateDocument(Document document) {
        if (document.formatVersion != V1 && document.formatVersion != V2) {
            throw new IllegalArgumentException("지원하지 않는 운동 전송 버전입니다.");
        }
        requireNonBlank(document.sourceApp, "sourceApp");
        if (document.sessions == null) {
            throw new IllegalArgumentException("운동 전송 sessions가 없습니다.");
        }
        for (Session session : document.sessions) {
            if (session == null) {
                throw new IllegalArgumentException("운동 전송 세션이 비어 있습니다.");
            }
            requireNonBlank(session.sourceApp, "sourceApp");
            requireNonBlank(session.sourceRecordId, "sourceRecordId");
            requireNonBlank(session.date, "date");
            if (session.exercises == null) {
                throw new IllegalArgumentException("운동 전송 exercises가 없습니다.");
            }
            for (Exercise exercise : session.exercises) {
                validateExercise(exercise, document.formatVersion);
            }
        }
    }

    private static void validateExercise(Exercise exercise, int version) {
        if (exercise == null) {
            throw new IllegalArgumentException("운동 전송 종목이 비어 있습니다.");
        }
        requireNonBlank(exercise.exerciseId, "exerciseId");
        if (exercise.orderIndex != null && exercise.orderIndex < 1) {
            throw new IllegalArgumentException("운동 전송 orderIndex가 올바르지 않습니다.");
        }
        if (exercise.sets == null) {
            throw new IllegalArgumentException("운동 전송 sets가 없습니다.");
        }
        for (SetData set : exercise.sets) {
            validateSet(set, version);
        }
    }

    private static void validateSet(SetData set, int version) {
        if (set == null || set.setIndex < 1) {
            throw new IllegalArgumentException("운동 전송 setIndex가 올바르지 않습니다.");
        }
        validateNonNegative(set.weightKg, "weightKg");
        validateNonNegative(set.volumeKg, "volumeKg");
        validateNonNegative(set.distanceMeters, "distanceMeters");
        validateNonNegative(set.assistedWeightKg, "assistedWeightKg");
        validateNonNegative(set.addedWeightKg, "addedWeightKg");
        if ((set.targetReps != null && set.targetReps < 0)
                || (set.actualReps != null && set.actualReps < 0)
                || (set.durationSeconds != null && set.durationSeconds < 0)
                || (set.restSeconds != null && set.restSeconds < 0)) {
            throw new IllegalArgumentException("운동 전송 반복·시간 값이 올바르지 않습니다.");
        }
        if (set.rpe != null && (set.rpe < 0 || set.rpe > 10)) {
            throw new IllegalArgumentException("운동 전송 RPE가 올바르지 않습니다.");
        }
        if (set.rir != null && (set.rir < 0 || set.rir > 5)) {
            throw new IllegalArgumentException("운동 전송 RIR가 올바르지 않습니다.");
        }

        int canonicalLoadCount = 0;
        if (set.weightKg != null) canonicalLoadCount += 1;
        if (set.addedWeightKg != null) canonicalLoadCount += 1;
        if (set.assistedWeightKg != null) canonicalLoadCount += 1;
        if (canonicalLoadCount > 1) {
            throw new IllegalArgumentException("운동 전송 세트의 canonical 중량 필드가 겹칩니다.");
        }

        LoadState state = LoadState.fromId(set.loadState);
        if (set.loadState != null && state == null) {
            throw new IllegalArgumentException("운동 전송 loadState가 올바르지 않습니다: " + set.loadState);
        }
        if (state != null && ((state == LoadState.EXTERNAL_LOAD && set.weightKg == null)
                || (state == LoadState.ADDED_WEIGHT && set.addedWeightKg == null)
                || (state == LoadState.ASSISTED && set.assistedWeightKg == null))) {
            throw new IllegalArgumentException("loadState와 canonical 중량 필드가 일치하지 않습니다.");
        }
        if (state != null && !isNumericState(state) && canonicalLoadCount > 0) {
            throw new IllegalArgumentException("중량을 저장하지 않는 loadState에 canonical 중량이 있습니다.");
        }

        if (version < V2) {
            return;
        }
        boolean hasInputValue = set.inputLoadValue != null;
        boolean hasInputUnit = set.inputLoadUnit != null;
        if (hasInputValue != hasInputUnit) {
            throw new IllegalArgumentException("inputLoadValue와 inputLoadUnit은 함께 있어야 합니다.");
        }
        if (!hasInputValue) {
            return;
        }
        validateNonNegative(set.inputLoadValue, "inputLoadValue");
        MassUnit unit = MassUnit.parse(set.inputLoadUnit);
        if (unit == null) {
            throw new IllegalArgumentException("지원하지 않는 inputLoadUnit입니다: " + set.inputLoadUnit);
        }
        Double canonicalLoad = set.weightKg != null
                ? set.weightKg
                : set.addedWeightKg != null ? set.addedWeightKg : set.assistedWeightKg;
        if (canonicalLoad == null) {
            throw new IllegalArgumentException("input provenance에는 canonical 중량이 필요합니다.");
        }
        double convertedKg = MassUnit.toKg(set.inputLoadValue, unit);
        double tolerance = Math.max(0.01d, Math.max(Math.abs(convertedKg), Math.abs(canonicalLoad)) * 0.0001d);
        if (Math.abs(convertedKg - canonicalLoad) > tolerance) {
            throw new IllegalArgumentException("input provenance와 canonical 중량이 일치하지 않습니다.");
        }
    }

    private static boolean isNumericState(LoadState state) {
        return state == LoadState.EXTERNAL_LOAD
                || state == LoadState.ADDED_WEIGHT
                || state == LoadState.ASSISTED;
    }

    private static void validateNonNegative(Double value, String label) {
        if (value != null && (!Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException(label + "은 0 이상의 유한한 값이어야 합니다.");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "이 없습니다.");
        }
    }

    private static String requiredString(JSONObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            throw new IllegalArgumentException(key + "이 없습니다.");
        }
        return value;
    }

    private static String optionalString(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key) && !object.isNull(key)) {
                String value = object.optString(key, null);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static Integer optionalInteger(JSONObject object, String... keys) {
        Object value = optionalValue(object, keys);
        if (value == null) return null;
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(keys[0] + "은 숫자여야 합니다.");
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number)) {
            throw new IllegalArgumentException(keys[0] + "은 정수여야 합니다.");
        }
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(keys[0] + "의 범위가 올바르지 않습니다.");
        }
        return (int) number;
    }

    private static int requiredInt(JSONObject object, String... keys) {
        Integer value = optionalInteger(object, keys);
        if (value == null) {
            throw new IllegalArgumentException(keys[0] + "이 없습니다.");
        }
        return value;
    }

    private static Double optionalDouble(JSONObject object, String... keys) {
        Object value = optionalValue(object, keys);
        if (value == null) return null;
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(keys[0] + "은 숫자여야 합니다.");
        }
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException(keys[0] + "은 유한한 숫자여야 합니다.");
        }
        return number;
    }

    private static boolean requiredBoolean(JSONObject object, String key) {
        Object value = optionalValue(object, key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(key + "은 boolean이어야 합니다.");
        }
        return (Boolean) value;
    }

    private static Object optionalValue(JSONObject object, String... keys) {
        if (object == null) return null;
        for (String key : keys) {
            if (object.has(key) && !object.isNull(key)) {
                return object.opt(key);
            }
        }
        return null;
    }

    private static void putNullable(JSONObject object, String key, Object value) throws Exception {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    public static final class Document {
        public final int formatVersion;
        public final String sourceApp;
        public final String exportedAt;
        public final List<Session> sessions;

        public Document(int formatVersion, String sourceApp, String exportedAt, List<Session> sessions) {
            this.formatVersion = formatVersion;
            this.sourceApp = sourceApp;
            this.exportedAt = exportedAt;
            this.sessions = immutable(sessions);
        }
    }

    public static final class Session {
        public final String sourceApp;
        public final String sourceRecordId;
        public final String date;
        public final String title;
        public final String workoutType;
        public final String category;
        public final Integer durationSeconds;
        public final String status;
        public final String startedAt;
        public final String endedAt;
        public final List<Exercise> exercises;

        public Session(
                String sourceApp,
                String sourceRecordId,
                String date,
                String title,
                String workoutType,
                String category,
                Integer durationSeconds,
                String status,
                String startedAt,
                String endedAt,
                List<Exercise> exercises
        ) {
            this.sourceApp = sourceApp;
            this.sourceRecordId = sourceRecordId;
            this.date = date;
            this.title = title;
            this.workoutType = workoutType;
            this.category = category;
            this.durationSeconds = durationSeconds;
            this.status = status;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.exercises = immutable(exercises);
        }

        public Session(
                String sourceApp,
                String sourceRecordId,
                String date,
                String title,
                String workoutType,
                String category,
                Integer durationSeconds,
                String startedAt,
                String endedAt,
                List<Exercise> exercises
        ) {
            this(
                    sourceApp,
                    sourceRecordId,
                    date,
                    title,
                    workoutType,
                    category,
                    durationSeconds,
                    null,
                    startedAt,
                    endedAt,
                    exercises
            );
        }
    }

    public static final class Exercise {
        public final String exerciseId;
        public final String canonicalExerciseId;
        public final String canonicalPresetId;
        public final String exerciseName;
        public final Integer orderIndex;
        public final String recordType;
        public final String uiPart;
        public final String equipment;
        public final List<SetData> sets;

        public Exercise(
                String exerciseId,
                String canonicalExerciseId,
                String canonicalPresetId,
                String exerciseName,
                Integer orderIndex,
                String recordType,
                String uiPart,
                String equipment,
                List<SetData> sets
        ) {
            this.exerciseId = exerciseId;
            this.canonicalExerciseId = canonicalExerciseId;
            this.canonicalPresetId = canonicalPresetId;
            this.exerciseName = exerciseName;
            this.orderIndex = orderIndex;
            this.recordType = recordType;
            this.uiPart = uiPart;
            this.equipment = equipment;
            this.sets = immutable(sets);
        }
    }

    public static final class SetData {
        public final int setIndex;
        public final Integer targetReps;
        public final Integer actualReps;
        public final Double weightKg;
        public final Double volumeKg;
        public final Integer durationSeconds;
        public final Double distanceMeters;
        public final Integer restSeconds;
        public final Double assistedWeightKg;
        public final Double addedWeightKg;
        public final String loadState;
        public final boolean isCompleted;
        public final Integer rpe;
        public final Integer rir;
        public final String memo;
        public final Double inputLoadValue;
        public final String inputLoadUnit;

        public SetData(
                int setIndex,
                Integer targetReps,
                Integer actualReps,
                Double weightKg,
                Double volumeKg,
                Integer durationSeconds,
                Double distanceMeters,
                Integer restSeconds,
                Double assistedWeightKg,
                Double addedWeightKg,
                String loadState,
                boolean isCompleted,
                Integer rpe,
                Integer rir,
                String memo,
                Double inputLoadValue,
                String inputLoadUnit
        ) {
            this.setIndex = setIndex;
            this.targetReps = targetReps;
            this.actualReps = actualReps;
            this.weightKg = weightKg;
            this.volumeKg = volumeKg;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.restSeconds = restSeconds;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.loadState = loadState;
            this.isCompleted = isCompleted;
            this.rpe = rpe;
            this.rir = rir;
            this.memo = memo;
            this.inputLoadValue = inputLoadValue;
            this.inputLoadUnit = inputLoadUnit;
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(
                values == null ? Collections.emptyList() : values
        ));
    }
}
