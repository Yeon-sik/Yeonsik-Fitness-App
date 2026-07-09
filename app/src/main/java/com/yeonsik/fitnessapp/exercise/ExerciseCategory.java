package com.yeonsik.fitnessapp.exercise;

import java.util.Collections;
import java.util.List;

public final class ExerciseCategory {
    public final BodyPart bodyPart;
    public final String nameKo;
    public final String description;
    public final List<SubPart> subParts;

    public ExerciseCategory(BodyPart bodyPart, String nameKo, String description, List<SubPart> subParts) {
        this.bodyPart = bodyPart;
        this.nameKo = nameKo;
        this.description = description;
        this.subParts = Collections.unmodifiableList(subParts);
    }

    public static final class SubPart {
        public final String id;
        public final String nameKo;

        public SubPart(String id, String nameKo) {
            this.id = id;
            this.nameKo = nameKo;
        }
    }
}
