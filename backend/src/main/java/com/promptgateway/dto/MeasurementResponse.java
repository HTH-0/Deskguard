package com.promptgateway.dto;

import com.promptgateway.gateway.GatewayResult;

import java.util.Map;

/**
 * 측정 모드가 켜져 있을 때만 돌려주는 응답 본문(개발용, docs/design.md 7.3절). 통과와 차단 모두 같은 형식이어서
 * 측정 도구가 모든 문장에서 같은 키로 읽을 수 있다. 해당하지 않는 값은 null이다.
 *
 * scores: 질문별 확률(패턴 차단과 판정 실패에서는 null), failureKind: 판정 호출 실패 차단일 때의 실패 종류,
 * patternId: 패턴 차단일 때의 패턴 ID. 일반 모드의 응답에는 이 정보가 없다(차단 이유를 공격자에게 알리지 않기 위해서이다).
 */
public record MeasurementResponse(String decision, String stage, Map<String, Double> scores, String modelVersion,
                                  String failureKind, String patternId) {

    public static MeasurementResponse from(GatewayResult result) {
        return new MeasurementResponse(
                result.decision() == GatewayResult.Decision.PASSED ? "PASS" : "BLOCK",
                result.stage().name(),
                result.judgeResult() == null ? null : result.judgeResult().scores(),
                result.judgeResult() == null ? null : result.judgeResult().modelVersion(),
                result.failureKind() == null ? null : result.failureKind().name(),
                result.patternId());
    }
}
