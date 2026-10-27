package com.promptgateway.dto;

/**
 * 차단되었을 때 돌려주는 응답 본문. 차단 단계, 패턴 ID, 확률은 싣지 않는다(공격자가 우회 방법을 학습하지 못하게 하기 위해서이다).
 * 패턴 차단, 판정 차단, 판정 실패 차단 모두 이 같은 객체를 돌려주므로 응답만 봐서는 이유를 알 수 없다.
 */
public record BlockedResponse(boolean blocked, String message) {

    public static final BlockedResponse INSTANCE = new BlockedResponse(true, "정책에 따라 요청이 차단되었습니다");
}
