package com.promptgateway.gateway;

import com.promptgateway.filter.PatternFilter;
import com.promptgateway.judge.BlockDecider;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.log.GatewayLogger.Timings;
import com.promptgateway.service.AnswerGenerator;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
 *
 * 어떤 경로로 끝나든 로그를 정확히 한 줄 남긴다(설계 결정 S4). 통과와 차단은 결과 값으로,
 * 예외로 끝나는 경로(입력 검증 실패, 예상하지 못한 오류, 응답 모델 오류)는 decision=ERROR로 남기고 예외는 그대로 다시 던진다.
 */
public class ChatGateway {

    private final InputValidator validator;
    private final PatternFilter patternFilter;
    private final Judge judge;
    private final BlockDecider blockDecider;
    private final AnswerGenerator answerGenerator;
    private final GatewayLogger logger;
    private final boolean measurementMode;

    public ChatGateway(InputValidator validator, PatternFilter patternFilter, Judge judge,
                       BlockDecider blockDecider, AnswerGenerator answerGenerator, GatewayLogger logger) {
        this(validator, patternFilter, judge, blockDecider, answerGenerator, logger, false);
    }

    public ChatGateway(InputValidator validator, PatternFilter patternFilter, Judge judge,
                       BlockDecider blockDecider, AnswerGenerator answerGenerator, GatewayLogger logger,
                       boolean measurementMode) {
        this.validator = validator;
        this.patternFilter = patternFilter;
        this.judge = judge;
        this.blockDecider = blockDecider;
        this.answerGenerator = answerGenerator;
        this.logger = logger;
        this.measurementMode = measurementMode;
    }

    public boolean isMeasurementMode() {
        return measurementMode;
    }

    public GatewayResult handle(String prompt) {
        return handle(prompt, measurementMode);
    }

    /**
     * measure가 true이면 측정 처리이다: 통과해도 응답 모델을 부르지 않고 답변 없이 판정 결과만 돌려준다.
     * 측정 처리를 할지는 컨트롤러가 요청마다 정한다(측정 모드 설정과 접속 주소, AC-9.7).
     */
    public GatewayResult handle(String prompt, boolean measure) {
        try {
            validator.validate(prompt);
        } catch (InvalidPromptException e) {
            logger.logError(prompt, "VALIDATION", e, Timings.NONE);
            throw e;
        }

        long filterStart = System.nanoTime();
        Optional<String> patternId = patternFilter.firstMatch(prompt);
        long filterMs = elapsedMs(filterStart);
        if (patternId.isPresent()) {
            return logged(prompt, GatewayResult.blockedByPattern(patternId.get()), new Timings(filterMs, null, null));
        }

        long judgeStart = System.nanoTime();
        JudgeResult judgeResult;
        boolean block;
        try {
            judgeResult = judge.judge(prompt);
            block = blockDecider.shouldBlock(judgeResult.scores());
        } catch (JudgeException e) {
            return logged(prompt, GatewayResult.blockedByJudgeFailure(e.kind()),
                    new Timings(filterMs, elapsedMs(judgeStart), null));
        } catch (RuntimeException e) {
            logger.logError(prompt, "JUDGE", e, new Timings(filterMs, elapsedMs(judgeStart), null));
            throw e;
        }
        long judgeMs = elapsedMs(judgeStart);
        if (block) {
            return logged(prompt, GatewayResult.blockedByJudge(judgeResult), new Timings(filterMs, judgeMs, null));
        }

        if (measure) {
            return logged(prompt, GatewayResult.passed(judgeResult, null), new Timings(filterMs, judgeMs, null));
        }

        long answerStart = System.nanoTime();
        String answer;
        try {
            answer = answerGenerator.generateAnswer(prompt);
        } catch (RuntimeException e) {
            logger.logError(prompt, "ANSWER", e, new Timings(filterMs, judgeMs, elapsedMs(answerStart)));
            throw e;
        }
        return logged(prompt, GatewayResult.passed(judgeResult, answer),
                new Timings(filterMs, judgeMs, elapsedMs(answerStart)));
    }

    private GatewayResult logged(String prompt, GatewayResult result, Timings timings) {
        logger.logResult(prompt, result, timings);
        return result;
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
