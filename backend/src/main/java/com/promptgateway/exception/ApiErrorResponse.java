package com.promptgateway.exception;

/**
 * 에러가 났을 때 사용자에게 돌려줄 JSON 모양을 통일하기 위한 클래스.
 * 이게 없으면 에러마다 스프링 기본 에러 페이지(HTML)나 제각각인 형식이 나가서,
 * 나중에 4단계에서 "차단 응답"과 "진짜 에러 응답"을 구분하기 어려워진다.
 */
public record ApiErrorResponse(String error, String message) {
}
