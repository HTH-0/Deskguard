package com.promptgateway.gateway;

/**
 * 요청의 prompt가 처리할 수 있는 입력인지 검사한다(FR-1).
 * 비어 있거나 공백뿐이면 거부하고, 최대 길이(기본 1,000자)를 넘으면 거부한다.
 * 글자 수는 이모지 같은 보충 문자도 한 글자로 세기 위해 유니코드 코드포인트 단위로 센다.
 */
public class InputValidator {

    private final int maxLength;

    public InputValidator(int maxLength) {
        this.maxLength = maxLength;
    }

    public void validate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new InvalidPromptException("prompt가 비어 있습니다.");
        }
        if (prompt.codePointCount(0, prompt.length()) > maxLength) {
            throw new InvalidPromptException("prompt는 " + maxLength + "자를 넘을 수 없습니다.");
        }
    }
}
