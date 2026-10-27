package com.promptgateway.judge;

/** 판정 모델(Jev)에 보내는 질문 하나. 참 기준과 거짓 기준이 경계를 정한다. */
public record Question(String id, String instruction, String trueCriteria, String falseCriteria) {
}
