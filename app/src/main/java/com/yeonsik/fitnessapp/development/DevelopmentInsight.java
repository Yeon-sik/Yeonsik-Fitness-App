package com.yeonsik.fitnessapp.development;

public final class DevelopmentInsight {
    public final String category;
    public final String title;
    public final String evidence;
    public final String nextAction;
    public final String limitation;

    public DevelopmentInsight(
            String category,
            String title,
            String evidence,
            String nextAction,
            String limitation
    ) {
        this.category = requireText(category, "카테고리");
        this.title = requireText(title, "제목");
        this.evidence = requireText(evidence, "근거");
        this.nextAction = requireText(nextAction, "다음 행동");
        this.limitation = requireText(limitation, "제한사항");
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "은 비어 있을 수 없습니다.");
        }
        return normalized;
    }
}
