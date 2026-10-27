package com.promptgateway.judge;

import java.util.List;
import java.util.Map;

/**
 * 질문별 확률을 보고 차단할지 정하는 순수 함수.
 * 외부 호출이 없어서 임계값 경계를 정확하게 시험할 수 있다.
 * 질문 하나라도 임계값 이상이면 차단하고(임계값과 같아도 차단), 기대한 질문이 하나라도 빠지면 판정 실패로 본다.
 */
public class BlockDecider {

    private final List<String> questionIds;
    private final double threshold;

    public BlockDecider(List<String> questionIds, double threshold) {
        this.questionIds = List.copyOf(questionIds);
        this.threshold = threshold;
    }

    public boolean shouldBlock(Map<String, Double> scores) {
        for (String id : questionIds) {
            if (scores.get(id) == null) {
                throw new JudgeException(JudgeException.Kind.MALFORMED_RESPONSE,
                        "판정 응답에 질문 " + id + "의 확률이 없습니다.");
            }
        }
        for (String id : questionIds) {
            if (scores.get(id) >= threshold) {
                return true;
            }
        }
        return false;
    }
}
