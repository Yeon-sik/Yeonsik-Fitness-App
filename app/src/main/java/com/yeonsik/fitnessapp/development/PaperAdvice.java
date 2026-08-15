package com.yeonsik.fitnessapp.development;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 사용자에게 보여줄 수 있는 근거 추적형 조언 한 건. */
public final class PaperAdvice {
    public enum Status { ACTIONABLE, INFORMATIONAL, INSUFFICIENT_DATA, SAFETY_REVIEW }

    public final String adviceId;
    public final String category;
    public final String titleKo;
    public final String observationKo;
    public final String recommendationKo;
    public final String limitationKo;
    public final String confidence;
    public final Status status;
    public final List<String> evidenceRefs;

    public PaperAdvice(
            String adviceId, String category, String titleKo, String observationKo,
            String recommendationKo, String limitationKo, String confidence,
            Status status, List<String> evidenceRefs
    ) {
        this.adviceId = requireText(adviceId, "조언 ID");
        this.category = requireText(category, "분류");
        this.titleKo = requireText(titleKo, "제목");
        this.observationKo = requireText(observationKo, "관찰");
        this.recommendationKo = requireText(recommendationKo, "권고");
        this.limitationKo = requireText(limitationKo, "제한사항");
        this.confidence = requireText(confidence, "신뢰도");
        if (status == null) throw new IllegalArgumentException("조언 상태가 필요합니다.");
        this.status = status;
        this.evidenceRefs = Collections.unmodifiableList(new ArrayList<>(
                evidenceRefs == null ? Collections.emptyList() : evidenceRefs
        ));
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + "은 비어 있을 수 없습니다.");
        return normalized;
    }
}
