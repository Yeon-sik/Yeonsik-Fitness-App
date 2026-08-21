package com.yeonsik.fitnessapp.development;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 한 번의 로컬 snapshot과 그 snapshot에서 나온 논문 조언을 함께 보존한다. */
public final class PaperAdviceAssessment {
    public final PaperAdviceInput input;
    public final List<PaperAdvice> advice;

    public PaperAdviceAssessment(PaperAdviceInput input, List<PaperAdvice> advice) {
        if (input == null) {
            throw new IllegalArgumentException("논문 조언 입력 snapshot이 필요합니다.");
        }
        this.input = input;
        this.advice = Collections.unmodifiableList(new ArrayList<>(
                advice == null ? Collections.emptyList() : advice
        ));
    }
}
