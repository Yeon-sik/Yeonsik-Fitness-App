package com.yeonsik.fitnessapp.sync;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import javax.net.ssl.SSLException;

/** Maps authentication failures to controlled copy without exposing server bodies or credentials. */
public final class SupabaseAuthErrors {
    private SupabaseAuthErrors() { }

    public static Failure responseFailure(int status, String code, String serverMessage) {
        String normalized = code == null ? "" : code.toLowerCase(Locale.ROOT);
        String message = serverMessage == null ? "" : serverMessage.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            if (message.contains("invalid api key")) normalized = "invalid_api_key";
            else if (message.contains("invalid login credentials")) normalized = "invalid_credentials";
            else if (message.contains("email not confirmed")) normalized = "email_not_confirmed";
        }
        return new Failure(status, normalized);
    }

    public static String messageFor(Exception error) {
        if (error instanceof Failure) return error.getMessage();
        if (error instanceof SocketTimeoutException) return "로그인 응답이 지연되었습니다. 잠시 후 다시 시도하세요.";
        if (error instanceof UnknownHostException) return "DB 주소에 연결하지 못했습니다. URL과 네트워크를 확인하세요.";
        if (error instanceof SSLException) return "DB의 보안 연결을 확인하지 못했습니다. HTTPS 주소를 확인하세요.";
        if ("DB 연결이 변경되었습니다. 새 연결에서 다시 로그인하세요.".equals(error.getMessage())) {
            return error.getMessage();
        }
        if (error instanceof IllegalStateException) {
            return "계정 연결을 적용하지 못했습니다. 로컬 기록의 계정 소유권과 저장 상태를 확인하세요.";
        }
        return "로그인을 완료하지 못했습니다. 연결 설정과 네트워크를 확인한 뒤 다시 시도하세요.";
    }

    public static final class Failure extends IOException {
        public final int statusCode;
        public final String code;

        public Failure(int statusCode, String code) {
            super(controlledMessage(statusCode, code));
            this.statusCode = statusCode;
            this.code = knownCode(code) ? code : "authentication_failed";
        }
    }

    private static boolean knownCode(String code) {
        switch (code == null ? "" : code) {
            case "connection_missing": case "connection_changed": case "input_missing":
            case "password_too_short": case "account_mismatch": case "invalid_response":
            case "invalid_api_key": case "invalid_credentials": case "email_not_confirmed":
            case "email_address_invalid": case "email_provider_disabled": case "provider_disabled":
            case "signup_disabled": case "user_banned": case "captcha_failed":
            case "over_request_rate_limit": case "over_email_send_rate_limit":
            case "refresh_token_not_found": case "refresh_token_already_used":
                return true;
            default: return false;
        }
    }

    private static String controlledMessage(int status, String code) {
        switch (code == null ? "" : code) {
            case "connection_missing": return "DB URL과 공개 API 키를 먼저 저장하세요.";
            case "connection_changed": return "DB 연결이 변경되었습니다. 새 연결에서 다시 로그인하세요.";
            case "input_missing": return "이메일과 비밀번호를 입력하세요.";
            case "password_too_short": return "이메일과 8자 이상의 비밀번호를 입력하세요.";
            case "account_mismatch": return "로컬 기록이 다른 계정에 연결되어 있습니다. 기존 기록의 계정으로 로그인하세요.";
            case "invalid_response": return "인증 서버가 올바른 로그인 세션을 반환하지 않았습니다. DB 연결을 확인하세요.";
            case "invalid_api_key": return "DB URL과 공개 API 키가 같은 프로젝트의 값인지 확인하세요.";
            case "invalid_credentials": return "이메일 또는 비밀번호가 올바르지 않습니다. 이 DB에 등록한 계정인지 확인하세요.";
            case "email_not_confirmed": return "이메일 인증이 필요합니다. 가입 확인 메일의 링크를 연 뒤 다시 로그인하세요.";
            case "email_address_invalid": return "이메일 주소 형식을 확인하세요.";
            case "email_provider_disabled": case "provider_disabled": return "이 DB에서 이메일 로그인이 활성화되어 있지 않습니다.";
            case "signup_disabled": return "이 DB에서는 신규 가입이 허용되지 않습니다. 기존 계정으로 로그인하세요.";
            case "user_banned": return "사용이 제한된 계정입니다. 계정 관리자에게 확인하세요.";
            case "captcha_failed": return "이 DB는 추가 인증을 요구합니다. 서버의 로그인 설정을 확인하세요.";
            case "refresh_token_not_found": case "refresh_token_already_used": return "로그인 세션이 만료되었습니다. 다시 로그인하세요.";
            case "over_request_rate_limit": case "over_email_send_rate_limit": return "로그인 요청이 너무 많습니다. 잠시 후 다시 시도하세요.";
            default:
                if (status == 429) return "로그인 요청이 너무 많습니다. 잠시 후 다시 시도하세요.";
                if (status >= 500) return "인증 서버에 일시적인 오류가 있습니다. 잠시 후 다시 시도하세요.";
                if (status == 401 || status == 403) return "인증이 거절되었습니다. DB 연결값과 계정 정보를 확인하세요.";
                return "로그인을 완료하지 못했습니다. 연결 설정과 계정 정보를 확인하세요.";
        }
    }
}
