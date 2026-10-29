package com.promptgateway.detect;

/** 처리 단계(docs/policy.md 2장). 순서가 강도이다: 뒤로 갈수록 강하다. */
public enum Action { ALLOW, MASK, WARN, BLOCK }
