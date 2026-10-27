package com.promptgateway.gateway;

import com.promptgateway.filter.PatternFilter;
import com.promptgateway.judge.BlockDecider;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.service.AnswerGenerator;

import java.util.Optional;

/**
 * 요청 하나가 거치는 단계를 순서대로 부르고 결과를 모으는 곳. 각 단계의 판단은 하지 않고 순서만 정한다.
 *
 *   1. 입력 검증      실패하면 InvalidPromptException (컨트롤러에서 400)
 *   2. 패턴 필터      일치하면 차단 (판정 모델은 부르지 않는다)
 *   3. 판정 모델      호출이 실패하거나 응답이 깨져 있으면 차단 (검사하지 못한 요청은 통과시키지 않는다)
 *   4. 차단 결정      질문 하나라도 임계값 이상이면 차단
 *   5. 응답 모델      통과한 요청만 답변을 만든다
 *
 * 응답 모델의 오류는 여기서 차단으로 바꾸지 않고 그대로 밖으로 알린다(기존 오류 응답 형식 유지).
 */
public class ChatGateway {

    private final InputValidator validator;
    private final PatternFilter patternFilter;
    private final Judge judge;
    private final BlockDecider blockDecider;
    private final AnswerGenerator answerGenerator;

    public ChatGateway(InputValidator validator, PatternFilter patternFilter, Judge judge,
                       BlockDecider blockDecider, AnswerGenerator answerGenerator) {
        this.validator = validator;
        this.patternFilter = patternFilter;
        this.judge = judge;
        this.blockDecider = blockDecider;
        this.answerGenerator = answerGenerator;
    }

    public GatewayResult handle(String prompt) {
        validator.validate(prompt);

        Optional<String> patternId = patternFilter.firstMatch(prompt);
        if (patternId.isPresent()) {
            return GatewayResult.blockedByPattern(patternId.get());
        }

        JudgeResult judgeResult;
        boolean block;
        try {
            judgeResult = judge.judge(prompt);
            block = blockDecider.shouldBlock(judgeResult.scores());
        } catch (JudgeException e) {
            return GatewayResult.blockedByJudgeFailure(e.kind());
        }
        if (block) {
            return GatewayResult.blockedByJudge(judgeResult);
        }

        String answer = answerGenerator.generateAnswer(prompt);
        return GatewayResult.passed(judgeResult, answer);
    }
}
