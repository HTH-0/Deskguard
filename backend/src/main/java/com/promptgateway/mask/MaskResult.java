package com.promptgateway.mask;

import java.util.Map;

/** 가린 문장과 가명 대응표. 대응표는 이 객체에만 있고 요청이 끝나면 버린다(AC-4.4). */
public record MaskResult(String maskedText, Map<String, String> pseudonymToValue) {
}
