package com.promptgateway.detect;

/**
 * 탐지 하나. start는 포함, end는 제외하는 문자 위치이다. value는 원문에서 잘라 낸 값이며 메모리에만 있다.
 * 로그와 응답(측정 모드 제외)에는 item과 개수만 싣는다.
 */
public record Finding(Item item, int start, int end, String value) {

    public Category category() {
        return item.category();
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(Finding other) {
        return start < other.end && other.start < end;
    }
}
