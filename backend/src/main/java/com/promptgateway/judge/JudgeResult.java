package com.promptgateway.judge;

import java.util.Map;

/**
 * 판정 한 번의 결과.
 * scores: 질문 ID별 "위반일 확률"(0~1), modelVersion: 실제로 응답한 판정 모델 버전, latencyMillis: 호출에 걸린 시간
 */
public record JudgeResult(Map<String, Double> scores, String modelVersion, long latencyMillis) {
}
