package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.Finding;
import com.promptgateway.detect.FindingCount;

import java.util.List;

/**
 * 게이트웨이가 요청 하나를 처리한 결과(명세 v2 1장). 차단과 경고는 예외가 아니라 이 값으로 돌려준다.
 *
 * action      처리. 경고를 확인해 보낸 요청은 MASK이고 confirmed가 true이다
 * counts      범주별 개수(응답과 로그용)
 * answer      외부 AI의 답(가림이면 복원한 답). 차단, 확인 전 경고, 측정 처리에서는 null
 * maskedPrompt 외부 AI로 보낸(측정 처리면 보낼) 가린 문장. 가림과 측정 처리에서 채운다. 로그에는 싣지 않는다
 * findings    측정 처리에서만 채운다(값과 위치가 들어 있으므로 일반 응답과 로그에 싣지 않는다)
 * restored    답에서 되돌린 가명 수
 */
public record GatewayResult(Action action, List<FindingCount> counts, String answer, boolean confirmed,
                            String maskedPrompt, List<Finding> findings, Timings timings, int restored, boolean measured) {

    /** 단계별 지연(밀리초). 실행하지 않은 단계는 null이다. */
    public record Timings(long detectMs, Long maskMs, Long answerMs, Long restoreMs) {
    }
}
