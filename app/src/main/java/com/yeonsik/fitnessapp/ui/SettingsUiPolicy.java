package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.core.ui.FitnessUiTokens;

/** Pure presentation policy shared by the Compose settings surface and tests. */
public final class SettingsUiPolicy {
    private SettingsUiPolicy() {}

    public static int syncStatusColorForLabel(String label) {
        if ("synced".equals(label) || "configured".equals(label)) return FitnessUiTokens.COLOR_POSITIVE;
        if ("sync failed".equals(label) || "authentication failed".equals(label)
                || "local ownership failed".equals(label) || "nutrition ownership failed".equals(label)) {
            return FitnessUiTokens.COLOR_NEGATIVE;
        }
        if ("syncing".equals(label) || "authenticating".equals(label) || "partial".equals(label)
                || "login required".equals(label) || "confirmation required".equals(label)) {
            return FitnessUiTokens.COLOR_WARNING;
        }
        return FitnessUiTokens.COLOR_TERTIARY;
    }

    public static String syncStatusLabel(String label) {
        switch (label == null ? "" : label) {
            case "synced": return "동기화 완료";
            case "configured": return "연결됨";
            case "syncing": return "동기화 중";
            case "authenticating": return "로그인 중";
            case "partial": return "일부 완료";
            case "login required": return "로그인 필요";
            case "confirmation required": return "가입 확인 필요";
            case "authentication failed": return "로그인 실패";
            case "local ownership failed":
            case "nutrition ownership failed": return "소유권 확인 실패";
            case "sync failed": return "동기화 실패";
            default: return "로컬 전용";
        }
    }

    public static UiState syncStateForLabel(String label) {
        if ("synced".equals(label) || "configured".equals(label)) return UiState.SUCCESS;
        if ("syncing".equals(label) || "authenticating".equals(label)) return UiState.LOADING;
        if ("login required".equals(label) || "confirmation required".equals(label)) return UiState.PERMISSION_REQUIRED;
        if ("partial".equals(label)) return UiState.SYNC_DELAYED;
        if ("authentication failed".equals(label) || "local ownership failed".equals(label)
                || "nutrition ownership failed".equals(label) || "sync failed".equals(label)) {
            return UiState.SERVER_ERROR;
        }
        return UiState.OFFLINE;
    }

    public static String safeSyncDetailForSurface(String label) {
        switch (label == null ? "" : label) {
            case "synced": return "최근 동기화가 완료되었습니다.";
            case "configured": return "계정 연결이 완료되었습니다.";
            case "syncing": return "기록을 서비스와 맞추는 중입니다.";
            case "authenticating": return "계정을 확인하는 중입니다.";
            case "partial": return "일부 기록만 동기화되었습니다. 다시 시도해 주세요.";
            case "login required": return "계정에 로그인하면 동기화를 사용할 수 있습니다.";
            case "confirmation required": return "가입 확인 메일을 확인한 뒤 로그인하세요.";
            case "authentication failed": return "로그인 정보를 확인한 뒤 다시 시도하세요.";
            case "local ownership failed":
            case "nutrition ownership failed": return "기록 소유권을 확인하지 못했습니다. 로컬 기록은 유지됩니다.";
            case "sync failed": return "연결을 확인한 뒤 다시 시도하세요.";
            default: return "현재 기록은 이 기기에 안전하게 보관됩니다.";
        }
    }
}
