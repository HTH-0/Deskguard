package com.promptgateway.mask;

/** 복원한 답과, 답에서 찾아 되돌린 가명의 수. */
public record RestoreResult(String text, int restored) {
}
