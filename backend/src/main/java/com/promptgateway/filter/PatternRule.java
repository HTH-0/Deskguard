package com.promptgateway.filter;

/** 패턴 필터의 규칙 하나. id는 로그와 측정 결과에서 어떤 패턴이 차단했는지 알아보는 데 쓴다. */
public record PatternRule(String id, String regex) {
}
