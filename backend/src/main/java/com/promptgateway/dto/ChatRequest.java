package com.promptgateway.dto;

/**
 * 우리 게이트웨이의 /api/chat 이 받는 요청 본문.
 * 사용자는 그냥 {"prompt": "안녕"} 같은 JSON만 보내면 된다.
 *
 * record는 자바 16+에서 생긴 문법으로, "값을 담기만 하는 클래스"를 짧게 쓰게 해준다.
 * record ChatRequest(String prompt) {} 한 줄이 사실상 아래와 같은 걸 자동으로 만들어준다:
 *   - private final String prompt; 필드
 *   - 생성자 ChatRequest(String prompt)
 *   - getter 역할을 하는 prompt() 메서드 (자바 관례인 getPrompt()가 아니라 prompt() 로 불림)
 * Jackson(JSON 변환 라이브러리)이 record를 자동으로 인식해서, HTTP body의 JSON을
 * 이 객체로 알아서 채워준다 - 우리가 파싱 코드를 직접 안 짜도 된다.
 */
public record ChatRequest(String prompt) {
}
