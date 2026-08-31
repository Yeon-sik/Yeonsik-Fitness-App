package com.yeonsik.fitnessapp.routine;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.BodyPart;
import com.yeonsik.fitnessapp.exercise.EquipmentType;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.ExerciseMasterAdapter;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RuntimeExercisePreset;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Converts a completed workout snapshot into a reusable routine without copying set data. */
public final class WorkoutRoutineMapper {
    private WorkoutRoutineMapper() {
    }

    public static List<RoutineExercise> mapCompletedExercises(
            List<FitnessRepository.SessionExerciseEntry> sessionExercises,
            Map<String, List<FitnessRepository.SessionSetEntry>> setsByExercise,
            RuntimeExerciseCatalog catalog
    ) {
        if (sessionExercises == null || sessionExercises.isEmpty()) {
            return Collections.emptyList();
        }
        RuntimeExerciseCatalog appliedCatalog = catalog == null
                ? RuntimeExerciseCatalog.empty()
                : catalog;
        List<RoutineExercise> result = new ArrayList<>();
        for (FitnessRepository.SessionExerciseEntry entry : sessionExercises) {
            if (entry == null || !hasCompletedSets(setsByExercise == null
                    ? null
                    : setsByExercise.get(entry.id))) {
                continue;
            }
            RuntimeExercisePreset preset = resolvePreset(entry, appliedCatalog);
            result.add(preset == null
                    ? preserveUnmappedEntry(entry)
                    : preserveCanonicalEntry(entry, preset));
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean hasCompletedSets(List<FitnessRepository.SessionSetEntry> sets) {
        if (sets == null) {
            return false;
        }
        for (FitnessRepository.SessionSetEntry set : sets) {
            if (set != null && set.isCompleted) {
                return true;
            }
        }
        return false;
    }

    private static RuntimeExercisePreset resolvePreset(
            FitnessRepository.SessionExerciseEntry entry,
            RuntimeExerciseCatalog catalog
    ) {
        RuntimeExercisePreset preset = null;
        if (entry.familyIdentity != null) {
            preset = catalog.preset(entry.familyIdentity.presetId);
            if (preset == null) {
                preset = catalog.preset(entry.familyIdentity.canonicalPresetId);
            }
        }
        if (preset == null) {
            preset = catalog.presetForStorageExerciseId(entry.exerciseId);
        }
        if (preset == null) {
            preset = catalog.presetForExactName(entry.name);
        }
        return preset;
    }

    private static RoutineExercise preserveCanonicalEntry(
            FitnessRepository.SessionExerciseEntry entry,
            RuntimeExercisePreset preset
    ) {
        RoutineExercise canonical = ExerciseMasterAdapter.toRoutineExercise(preset);
        if (canonical == null) {
            return preserveUnmappedEntry(entry);
        }
        return new RoutineExercise(
                canonical.masterExerciseId,
                canonical.nameKo,
                canonical.nameEn,
                canonical.bodyPart,
                canonical.equipmentType,
                canonical.equipmentVariantId,
                canonical.primarySubPart,
                FitnessRecordContract.normalizeRecordType(entry.recordType),
                canonical.familyIdentity
        );
    }

    private static RoutineExercise preserveUnmappedEntry(
            FitnessRepository.SessionExerciseEntry entry
    ) {
        BodyPart bodyPart = BodyPart.fromId(FitnessRecordContract.categoryCode(entry.uiPart));
        EquipmentType equipmentType = equipmentTypeFromValue(entry.equipment);
        ExerciseFamilyIdentity identity = entry.familyIdentity;
        String exerciseId = entry.exerciseId == null || entry.exerciseId.trim().isEmpty()
                ? "manual"
                : entry.exerciseId;
        String name = entry.name == null || entry.name.trim().isEmpty()
                ? "운동"
                : entry.name;
        return new RoutineExercise(
                exerciseId,
                name,
                name,
                bodyPart,
                equipmentType,
                equipmentType == null ? null : equipmentType.id(),
                entry.uiPart,
                FitnessRecordContract.normalizeRecordType(entry.recordType),
                identity
        );
    }

    private static EquipmentType equipmentTypeFromValue(String value) {
        EquipmentType equipmentType = EquipmentType.fromId(value);
        if (equipmentType != null) {
            return equipmentType;
        }
        if (value != null) {
            for (EquipmentType candidate : EquipmentType.values()) {
                if (candidate.labelKo().equals(value.trim())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
