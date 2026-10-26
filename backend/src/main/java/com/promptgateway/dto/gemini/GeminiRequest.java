package com.promptgateway.dto.gemini;

import java.util.List;

/**
 * 우리가 Gemini API로 보내는 요청 body 전체 모양.
 * 1단계 PowerShell에서 만들었던 이 구조를 그대로 자바 객체로 옮긴 것:
 *   { "contents": [ { "parts": [ { "text": "..." } ] } ] }
 */
public record GeminiRequest(List<Content> contents) {

    // 프롬프트 문자열 하나로 바로 요청 객체를 만들 수 있게 해주는 보조 생성자
    public static GeminiRequest of(String prompt) {
        return new GeminiRequest(List.of(Content.of(prompt)));
    }
}
