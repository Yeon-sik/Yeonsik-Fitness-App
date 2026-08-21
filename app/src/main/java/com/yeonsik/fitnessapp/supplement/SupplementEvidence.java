package com.yeonsik.fitnessapp.supplement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A reviewed evidence card. It describes research fit and never validates a user's dose. */
public final class SupplementEvidence {
    public enum Status {
        VERIFIED_DIRECT,
        CONTEXT_REQUIRED,
        LIMITED_OR_MIXED
    }

    public final String supplementTypeCode;
    public final Status status;
    public final String statusLabel;
    public final String summaryKo;
    public final String applicabilityKo;
    public final String limitationsKo;
    public final String safetyKo;
    public final String reviewedOn;
    public final List<Source> sources;

    public SupplementEvidence(
            String supplementTypeCode,
            Status status,
            String statusLabel,
            String summaryKo,
            String applicabilityKo,
            String limitationsKo,
            String safetyKo,
            String reviewedOn,
            List<Source> sources
    ) {
        this.supplementTypeCode = requireText(supplementTypeCode);
        if (status == null) throw new IllegalArgumentException("근거 상태가 필요합니다.");
        this.status = status;
        this.statusLabel = requireText(statusLabel);
        this.summaryKo = requireText(summaryKo);
        this.applicabilityKo = requireText(applicabilityKo);
        this.limitationsKo = requireText(limitationsKo);
        this.safetyKo = requireText(safetyKo);
        this.reviewedOn = requireText(reviewedOn);
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    private static String requireText(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("근거 필드는 비어 있을 수 없습니다.");
        return normalized;
    }

    public static final class Source {
        public final String evidenceId;
        public final String title;
        public final String citation;
        public final String verificationStatus;
        public final String url;

        public Source(String evidenceId, String title, String citation,
                      String verificationStatus, String url) {
            this.evidenceId = requireText(evidenceId);
            this.title = requireText(title);
            this.citation = requireText(citation);
            this.verificationStatus = requireText(verificationStatus);
            this.url = requireText(url);
        }
    }
}
