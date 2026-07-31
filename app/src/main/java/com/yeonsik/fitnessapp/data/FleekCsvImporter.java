package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.WeightExercise;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** FLEEK CSV를 repository가 한 번에 저장할 수 있는 세션/운동/세트 구조로 변환한다. */
public final class FleekCsvImporter {
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private FleekCsvImporter() {
    }

    public static ImportPlan parse(Reader reader, List<WeightExercise> masterExercises) throws IOException {
        List<List<String>> rows = readCsv(reader);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV 파일이 비어 있습니다.");
        }

        Map<String, Integer> header = headerIndexes(rows.get(0));
        int dateIndex = requiredColumn(header, "date");
        int exerciseIndex = requiredColumn(header, "exercise");
        int repsIndex = requiredColumn(header, "reps");
        int weightIndex = optionalColumn(header, "weight(kg)");
        int durationIndex = optionalColumn(header, "duration(s)");
        int distanceIndex = optionalColumn(header, "distance(m)");
        int rpeIndex = optionalColumn(header, "rpe");
        int setTypeIndex = optionalColumn(header, "set type");
        int gripTypeIndex = optionalColumn(header, "grip type");

        MasterMatcher matcher = new MasterMatcher(masterExercises);
        Map<String, SessionBuilder> sessions = new LinkedHashMap<>();
        int skippedRows = 0;
        int matchedSets = 0;

        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (isBlankRow(row)) {
                continue;
            }

            try {
                ParsedTimestamp timestamp = parseTimestamp(cell(row, dateIndex));
                String exerciseName = cell(row, exerciseIndex).trim();
                if (exerciseName.isEmpty()) {
                    throw new IllegalArgumentException("운동명이 없습니다.");
                }

                Integer reps = positiveInteger(cell(row, repsIndex));
                Double weightKg = positiveDouble(cell(row, weightIndex));
                Integer durationSeconds = positiveInteger(cell(row, durationIndex));
                Double distanceMeters = positiveDouble(cell(row, distanceIndex));
                Integer rpe = rpe(cell(row, rpeIndex));
                if (reps == null && durationSeconds == null) {
                    throw new IllegalArgumentException("횟수와 시간 값이 모두 없습니다.");
                }

                WeightExercise master = matcher.find(exerciseName);
                ExerciseTemplate template = exerciseTemplate(exerciseName, master, weightKg, durationSeconds);
                if (master != null) {
                    matchedSets += 1;
                }

                SessionBuilder session = sessions.get(timestamp.sourceKey);
                if (session == null) {
                    session = new SessionBuilder(timestamp);
                    sessions.put(timestamp.sourceKey, session);
                }

                ExerciseBuilder exercise = session.currentExercise(exerciseName, template);
                exercise.sets.add(setData(
                        exercise.sets.size() + 1,
                        template.recordType,
                        weightKg,
                        reps,
                        durationSeconds,
                        distanceMeters,
                        rpe,
                        cell(row, setTypeIndex),
                        cell(row, gripTypeIndex)
                ));
            } catch (IllegalArgumentException error) {
                skippedRows += 1;
            }
        }

        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("가져올 수 있는 FLEEK 운동 기록이 없습니다.");
        }

        List<SessionData> result = new ArrayList<>();
        for (SessionBuilder builder : sessions.values()) {
            result.add(builder.build());
        }
        return new ImportPlan(rows.size() - 1, skippedRows, matchedSets, result);
    }

    static String normalizeExerciseName(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                result.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static ExerciseTemplate exerciseTemplate(
            String sourceName,
            WeightExercise master,
            Double weightKg,
            Integer durationSeconds
    ) {
        String masterType = master == null
                ? ""
                : FitnessRecordContract.normalizeRecordType(master.recordType);
        String recordType = resolveRecordType(sourceName, masterType, weightKg, durationSeconds);

        if (master != null) {
            BodyPart bodyPart = master.bodyPart;
            EquipmentType equipment = master.equipmentType;
            String uiPart = bodyPart == null ? inferBodyPart(sourceName) : bodyPart.id();
            String primarySubPart = nonBlank(master.primarySubPartNameKo,
                    bodyPart == null ? displayBodyPart(uiPart) : bodyPart.labelKo());
            String equipmentSnapshot = equipment == null
                    ? inferEquipment(sourceName, weightKg).labelKo()
                    : equipment.labelKo();
            return new ExerciseTemplate(
                    nonBlank(master.id, "manual"),
                    sourceName,
                    uiPart,
                    primarySubPart,
                    equipmentSnapshot,
                    recordType,
                    true
            );
        }

        String uiPart = inferBodyPart(sourceName);
        return new ExerciseTemplate(
                "manual",
                sourceName,
                uiPart,
                displayBodyPart(uiPart),
                inferEquipment(sourceName, weightKg).labelKo(),
                recordType,
                false
        );
    }

    private static String resolveRecordType(
            String exerciseName,
            String masterType,
            Double weightKg,
            Integer durationSeconds
    ) {
        if (durationSeconds != null) {
            return weightKg == null ? FitnessRecordContract.TIME : FitnessRecordContract.WEIGHT_TIME;
        }
        if (weightKg == null) {
            return FitnessRecordContract.REPS_ONLY;
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(masterType)
                || looksAssisted(exerciseName)) {
            return FitnessRecordContract.ASSISTED_WEIGHT_REPS;
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(masterType)) {
            return FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;
        }
        return FitnessRecordContract.WEIGHT_REPS;
    }

    private static SetData setData(
            int setIndex,
            String recordType,
            Double sourceWeightKg,
            Integer reps,
            Integer durationSeconds,
            Double distanceMeters,
            Integer rpe,
            String setType,
            String gripType
    ) {
        Double weightKg = null;
        Double assistedWeightKg = null;
        Double addedWeightKg = null;
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(recordType)) {
            assistedWeightKg = sourceWeightKg;
        } else if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(recordType)) {
            addedWeightKg = sourceWeightKg;
        } else if (FitnessRecordContract.WEIGHT_REPS.equals(recordType)
                || FitnessRecordContract.WEIGHT_TIME.equals(recordType)) {
            weightKg = sourceWeightKg;
        }
        return new SetData(
                setIndex,
                weightKg,
                reps,
                durationSeconds,
                distanceMeters,
                assistedWeightKg,
                addedWeightKg,
                rpe,
                cleanOptional(setType),
                cleanOptional(gripType)
        );
    }

    private static String inferBodyPart(String exerciseName) {
        String name = normalizeExerciseName(exerciseName);
        if (containsAny(name, "크런치", "니업", "레그레이즈")) return "abs";
        if (containsAny(name, "트라이셉", "푸시다운")) return "triceps";
        if (containsAny(name, "바이셉", "컬", "프리쳐")) return "biceps";
        if (containsAny(name, "스쿼트", "레그", "힙", "데드리프트")) return "legs";
        if (containsAny(name, "숄더", "레터럴", "리어델트", "업라이트")) return "shoulders";
        if (containsAny(name, "로우", "랫풀", "풀업", "친업", "스트레이트암")) return "back";
        if (containsAny(name, "벤치", "체스트", "플라이", "푸시업")) return "chest";
        return "arms";
    }

    private static String displayBodyPart(String uiPart) {
        switch (uiPart) {
            case "chest": return "가슴";
            case "back": return "등";
            case "legs": return "하체";
            case "shoulders": return "어깨";
            case "abs": return "복근";
            case "biceps": return "이두";
            case "triceps": return "삼두";
            default: return "팔";
        }
    }

    private static EquipmentType inferEquipment(String exerciseName, Double weightKg) {
        String name = normalizeExerciseName(exerciseName);
        if (name.contains("스미스")) return EquipmentType.SMITH_MACHINE;
        if (name.contains("덤벨")) return EquipmentType.DUMBBELL;
        if (name.contains("바벨") || name.contains("이지바")) return EquipmentType.BARBELL;
        if (name.contains("케이블")) return EquipmentType.CABLE;
        if (name.contains("플레이트")) return EquipmentType.PLATE;
        if (name.contains("머신")) return EquipmentType.MACHINE;
        if (weightKg == null) return EquipmentType.BODYWEIGHT;
        return EquipmentType.OTHER;
    }

    private static boolean looksAssisted(String exerciseName) {
        String normalized = normalizeExerciseName(exerciseName);
        return normalized.contains("어시스티드") || normalized.contains("보조머신");
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    private static ParsedTimestamp parseTimestamp(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("운동 날짜가 없습니다.");
        }
        try {
            Instant instant = OffsetDateTime.parse(trimmed).toInstant();
            return timestamp(instant);
        } catch (DateTimeParseException ignored) {
            // Offset이 없는 내보내기 형식은 한국 로컬 시각으로 해석한다.
        }
        try {
            Instant instant = LocalDateTime.parse(trimmed).atZone(KOREA_ZONE).toInstant();
            return timestamp(instant);
        } catch (DateTimeParseException ignored) {
            // 날짜만 있는 CSV도 세션 시작 시각 00:00으로 가져온다.
        }
        try {
            Instant instant = LocalDate.parse(trimmed).atStartOfDay(KOREA_ZONE).toInstant();
            return timestamp(instant);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("운동 날짜 형식을 읽지 못했습니다.", error);
        }
    }

    private static ParsedTimestamp timestamp(Instant instant) {
        String sourceTimestamp = instant.atOffset(ZoneOffset.UTC).toString();
        return new ParsedTimestamp(
                instant.toString(),
                sourceTimestamp,
                instant.atZone(KOREA_ZONE).toLocalDate().toString()
        );
    }

    private static Map<String, Integer> headerIndexes(List<String> headerRow) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            String value = headerRow.get(index);
            if (index == 0 && value != null && value.startsWith("\uFEFF")) {
                value = value.substring(1);
            }
            indexes.put(value == null ? "" : value.trim().toLowerCase(Locale.ROOT), index);
        }
        return indexes;
    }

    private static int requiredColumn(Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        if (index == null) {
            throw new IllegalArgumentException("FLEEK CSV 필수 열이 없습니다: " + name);
        }
        return index;
    }

    private static int optionalColumn(Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        return index == null ? -1 : index;
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) return "";
        String value = row.get(index);
        return value == null ? "" : value;
    }

    private static Integer positiveInteger(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned.isEmpty()) return null;
        try {
            int parsed = Integer.parseInt(cleaned);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("정수 값을 읽지 못했습니다: " + value, error);
        }
    }

    private static Double positiveDouble(String value) {
        String cleaned = cleanOptional(value);
        if (cleaned.isEmpty()) return null;
        try {
            double parsed = Double.parseDouble(cleaned);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("숫자 값을 읽지 못했습니다: " + value, error);
        }
    }

    private static Integer rpe(String value) {
        Integer parsed = positiveInteger(value);
        return parsed != null && parsed <= 10 ? parsed : null;
    }

    private static String cleanOptional(String value) {
        String cleaned = value == null ? "" : value.trim();
        return "-".equals(cleaned) ? "" : cleaned;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) return false;
        }
        return true;
    }

    private static List<List<String>> readCsv(Reader source) throws IOException {
        PushbackReader reader = new PushbackReader(source, 1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int current;
        while ((current = reader.read()) != -1) {
            char value = (char) current;
            if (quoted) {
                if (value == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next != -1) reader.unread(next);
                    }
                } else {
                    field.append(value);
                }
                continue;
            }

            if (value == '"' && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (value == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (value == '\n' || value == '\r') {
                if (value == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(value);
                fieldStarted = true;
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("닫히지 않은 CSV 따옴표가 있습니다.");
        }
        if (field.length() > 0 || fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    public static final class ImportPlan {
        public final int sourceRows;
        public final int skippedRows;
        public final int matchedSets;
        public final List<SessionData> sessions;

        private ImportPlan(int sourceRows, int skippedRows, int matchedSets, List<SessionData> sessions) {
            this.sourceRows = sourceRows;
            this.skippedRows = skippedRows;
            this.matchedSets = matchedSets;
            this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
        }

        public int importableSets() {
            int count = 0;
            for (SessionData session : sessions) {
                for (ExerciseData exercise : session.exercises) {
                    count += exercise.sets.size();
                }
            }
            return count;
        }
    }

    public static final class SessionData {
        public final String sourceKey;
        public final String sourceTimestamp;
        public final String date;
        public final String title;
        public final int durationSeconds;
        public final List<ExerciseData> exercises;

        private SessionData(
                String sourceKey,
                String sourceTimestamp,
                String date,
                String title,
                int durationSeconds,
                List<ExerciseData> exercises
        ) {
            this.sourceKey = sourceKey;
            this.sourceTimestamp = sourceTimestamp;
            this.date = date;
            this.title = title;
            this.durationSeconds = durationSeconds;
            this.exercises = Collections.unmodifiableList(new ArrayList<>(exercises));
        }
    }

    public static final class ExerciseData {
        public final String exerciseId;
        public final String name;
        public final String uiPart;
        public final String primarySubPart;
        public final String equipment;
        public final String recordType;
        public final boolean masterMatched;
        public final List<SetData> sets;

        private ExerciseData(ExerciseTemplate template, List<SetData> sets) {
            this.exerciseId = template.exerciseId;
            this.name = template.name;
            this.uiPart = template.uiPart;
            this.primarySubPart = template.primarySubPart;
            this.equipment = template.equipment;
            this.recordType = template.recordType;
            this.masterMatched = template.masterMatched;
            this.sets = Collections.unmodifiableList(new ArrayList<>(sets));
        }
    }

    public static final class SetData {
        public final int setIndex;
        public final Double weightKg;
        public final Integer reps;
        public final Integer durationSeconds;
        public final Double distanceMeters;
        public final Double assistedWeightKg;
        public final Double addedWeightKg;
        public final Integer rpe;
        public final String setType;
        public final String gripType;

        private SetData(
                int setIndex,
                Double weightKg,
                Integer reps,
                Integer durationSeconds,
                Double distanceMeters,
                Double assistedWeightKg,
                Double addedWeightKg,
                Integer rpe,
                String setType,
                String gripType
        ) {
            this.setIndex = setIndex;
            this.weightKg = weightKg;
            this.reps = reps;
            this.durationSeconds = durationSeconds;
            this.distanceMeters = distanceMeters;
            this.assistedWeightKg = assistedWeightKg;
            this.addedWeightKg = addedWeightKg;
            this.rpe = rpe;
            this.setType = setType;
            this.gripType = gripType;
        }
    }

    private static final class MasterMatcher {
        private final Map<String, WeightExercise> exact = new HashMap<>();
        private final Map<String, WeightExercise> normalized = new HashMap<>();
        private final Set<String> ambiguous = new HashSet<>();

        MasterMatcher(List<WeightExercise> exercises) {
            if (exercises == null) return;
            for (WeightExercise exercise : exercises) {
                if (exercise == null || exercise.nameKo == null || exercise.nameKo.trim().isEmpty()) continue;
                exact.put(exercise.nameKo.trim(), exercise);
                String key = normalizeExerciseName(exercise.nameKo);
                if (normalized.containsKey(key)) {
                    ambiguous.add(key);
                    normalized.remove(key);
                } else if (!ambiguous.contains(key)) {
                    normalized.put(key, exercise);
                }
            }
        }

        WeightExercise find(String name) {
            WeightExercise direct = exact.get(name.trim());
            return direct == null ? normalized.get(normalizeExerciseName(name)) : direct;
        }
    }

    private static final class ParsedTimestamp {
        final String sourceKey;
        final String sourceTimestamp;
        final String date;

        ParsedTimestamp(String sourceKey, String sourceTimestamp, String date) {
            this.sourceKey = sourceKey;
            this.sourceTimestamp = sourceTimestamp;
            this.date = date;
        }
    }

    private static final class ExerciseTemplate {
        final String exerciseId;
        final String name;
        final String uiPart;
        final String primarySubPart;
        final String equipment;
        final String recordType;
        final boolean masterMatched;

        ExerciseTemplate(
                String exerciseId,
                String name,
                String uiPart,
                String primarySubPart,
                String equipment,
                String recordType,
                boolean masterMatched
        ) {
            this.exerciseId = exerciseId;
            this.name = name;
            this.uiPart = uiPart;
            this.primarySubPart = primarySubPart;
            this.equipment = equipment;
            this.recordType = recordType;
            this.masterMatched = masterMatched;
        }
    }

    private static final class ExerciseBuilder {
        final ExerciseTemplate template;
        final List<SetData> sets = new ArrayList<>();

        ExerciseBuilder(ExerciseTemplate template) {
            this.template = template;
        }

        ExerciseData build() {
            return new ExerciseData(template, sets);
        }
    }

    private static final class SessionBuilder {
        final ParsedTimestamp timestamp;
        final List<ExerciseBuilder> exercises = new ArrayList<>();

        SessionBuilder(ParsedTimestamp timestamp) {
            this.timestamp = timestamp;
        }

        ExerciseBuilder currentExercise(String sourceName, ExerciseTemplate template) {
            if (!exercises.isEmpty()) {
                ExerciseBuilder last = exercises.get(exercises.size() - 1);
                if (last.template.name.equals(sourceName)) return last;
            }
            ExerciseBuilder next = new ExerciseBuilder(template);
            exercises.add(next);
            return next;
        }

        SessionData build() {
            List<ExerciseData> builtExercises = new ArrayList<>();
            int durationSeconds = 0;
            Set<String> names = new LinkedHashSet<>();
            for (ExerciseBuilder exercise : exercises) {
                ExerciseData built = exercise.build();
                builtExercises.add(built);
                names.add(built.name);
                for (SetData set : built.sets) {
                    if (set.durationSeconds != null) durationSeconds += set.durationSeconds;
                }
            }
            String firstName = names.isEmpty() ? "운동" : names.iterator().next();
            String title = names.size() > 1
                    ? "FLEEK · " + firstName + " 외 " + (names.size() - 1) + "종목"
                    : "FLEEK · " + firstName;
            return new SessionData(
                    timestamp.sourceKey,
                    timestamp.sourceTimestamp,
                    timestamp.date,
                    title,
                    durationSeconds,
                    builtExercises
            );
        }
    }
}
