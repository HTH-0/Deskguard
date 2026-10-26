package com.promptgateway.dto;

/**
 * /api/chat 이 최종적으로 사용자에게 돌려주는 응답 본문.
 * Gemini가 준 복잡한 JSON(candidates -> content -> parts -> text ...)을
 * GeminiService가 다 해석해서, 우리 사용자에게는 이 단순한 형태로만 보여준다.
 * -> 이게 "게이트웨이"가 하는 일 중 하나: 외부 API의 복잡한 응답 구조를 그대로 노출하지 않고 감싸서 준다.
 */
public record ChatResponse(String answer) {
}
