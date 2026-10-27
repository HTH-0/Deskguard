package com.promptgateway.gateway;

/** 입력 검증(FR-1)에 실패했음을 알리는 예외. 컨트롤러 단계에서 400으로 바뀐다. */
public class InvalidPromptException extends RuntimeException {

    public InvalidPromptException(String message) {
        super(message);
    }
}
