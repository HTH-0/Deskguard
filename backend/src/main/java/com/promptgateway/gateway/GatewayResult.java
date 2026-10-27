package com.promptgateway.gateway;

import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;

/**
 * 게이트웨이가 요청 하나를 처리한 결과. "차단"은 예외가 아니라 이 값으로 돌려준다(차단은 정상 동작이기 때문이다).
 *
 * decision: 통과(PASSED) 또는 차단(BLOCKED)
 * stage: 어디에서 막혔는지. NONE(통과), PATTERN(패턴 필터), JUDGE(판정 모델이 위반으로 판단), JUDGE_FAILURE(판정 호출 실패)
 * patternId: 패턴 필터가 막았을 때 그 패턴의 ID
 * failureKind: 판정 호출이 실패했을 때 실패 종류
 * judgeResult: 판정이 끝났다면 질문별 확률과 모델 버전 (패턴에서 막히거나 판정이 실패하면 없음)
 * answer: 통과했을 때 응답 모델의 답변
 */
public record GatewayResult(Decision decision, Stage stage, String patternId,
                            JudgeException.Kind failureKind, JudgeResult judgeResult, String answer) {

    public enum Decision { PASSED, BLOCKED }

    public enum Stage { NONE, PATTERN, JUDGE, JUDGE_FAILURE }

    public static GatewayResult passed(JudgeResult judgeResult, String answer) {
        return new GatewayResult(Decision.PASSED, Stage.NONE, null, null, judgeResult, answer);
    }

    public static GatewayResult blockedByPattern(String patternId) {
        return new GatewayResult(Decision.BLOCKED, Stage.PATTERN, patternId, null, null, null);
    }

    public static GatewayResult blockedByJudge(JudgeResult judgeResult) {
        return new GatewayResult(Decision.BLOCKED, Stage.JUDGE, null, null, judgeResult, null);
    }

    public static GatewayResult blockedByJudgeFailure(JudgeException.Kind kind) {
        return new GatewayResult(Decision.BLOCKED, Stage.JUDGE_FAILURE, null, kind, null, null);
    }
}
