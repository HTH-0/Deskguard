package com.promptgateway.dto.gemini;

/**
 * Gemini API의 가장 안쪽 조각. 1단계 학습노트의 JSON 구조에서
 * candidates[0].content.parts[0].text 의 "parts 안 원소 하나"에 해당한다.
 * 요청을 보낼 때도, 응답을 받을 때도 똑같은 모양이라 하나로 같이 쓴다.
 */
public record Part(String text) {
}
