package com.promptgateway.detect;

import java.util.List;

/**
 * 탐지 결과로 처리 단계를 정한다(docs/design.md 3장, AC-3.1~3.3). 외부 의존이 없는 순수 함수이다.
 *
 *   D2 자격증명이나 D3 등급 표시가 하나라도 있음 → 차단
 *   D1 개인정보가 대량 기준 이상               → 경고 (확인하면 가림으로 보낸다)
 *   D1이나 D3(코드명, 고객사)이 있음            → 가림
 *   없음                                      → 허용
 */
public class ActionDecider {

    private final int bulkThreshold;

    public ActionDecider(int bulkThreshold) {
        if (bulkThreshold <= 0) {
            throw new IllegalArgumentException("detection.bulk-threshold는 0보다 커야 합니다: " + bulkThreshold);
        }
        this.bulkThreshold = bulkThreshold;
    }

    public Action decide(List<Finding> findings) {
        if (findings.stream().anyMatch(f -> f.category() == Category.D2 || f.item() == Item.CLASSIFIED)) {
            return Action.BLOCK;
        }
        long personal = findings.stream().filter(f -> f.category() == Category.D1).count();
        if (personal >= bulkThreshold) {
            return Action.WARN;
        }
        return findings.isEmpty() ? Action.ALLOW : Action.MASK;
    }
}
