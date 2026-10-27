package com.promptgateway.judge;

/**
 * 판정 호출이 실패했음을 알리는 예외. ChatGateway가 이 예외를 잡아서 "차단"으로 바꾼다(fail-closed).
 * 실패 원인(Kind)은 로그와 측정 집계에서 정책 차단과 구별하려고 구분해 둔다.
 */
public class JudgeException extends RuntimeException {

    public enum Kind { TIMEOUT, RATE_LIMITED, SERVER_ERROR, CONFIG_ERROR, INPUT_TOO_LONG, MALFORMED_RESPONSE }

    private final Kind kind;

    public JudgeException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public JudgeException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
