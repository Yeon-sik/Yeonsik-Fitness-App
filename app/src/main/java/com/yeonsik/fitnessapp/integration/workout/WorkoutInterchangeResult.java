package com.yeonsik.fitnessapp.integration.workout;

import java.util.ArrayList;
import java.util.List;

/** Result shared by external workout import application services. */
public final class WorkoutInterchangeResult {
    public int importedSessions;
    public int importedExercises;
    public int importedSets;
    public int masterMatchedSets;
    public int skippedDuplicateSessions;
    public int skippedRows;

    public String summary() {
        List<String> parts = new ArrayList<>();
        parts.add("세션 " + importedSessions + "건");
        parts.add("세트 " + importedSets + "건");
        if (skippedDuplicateSessions > 0) {
            parts.add("중복 세션 " + skippedDuplicateSessions + "건 제외");
        }
        if (skippedRows > 0) {
            parts.add("해석 불가 행 " + skippedRows + "건 제외");
        }
        return String.join(" · ", parts);
    }
}
