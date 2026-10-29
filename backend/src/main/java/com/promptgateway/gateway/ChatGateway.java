package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.ActionDecider;
import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.Finding;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.mask.MaskResult;
import com.promptgateway.mask.Masker;
import com.promptgateway.mask.RestoreResult;
import com.promptgateway.service.AnswerGenerator;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 요청 하나가 거치는 단계를 순서대로 부른다(명세 v2 1장). 판단은 각 부품이 하고, 이 클래스는 순서만 정한다.
 *
 *   1. 입력 검증   실패하면 InvalidPromptException (컨트롤러에서 400)
 *   2. 탐지       게이트웨이 안의 코드만 쓴다. 외부 서비스를 부르지 않는다(NFR-4)
 *   3. 처리 결정   차단 > 경고 > 가림 > 허용. 경고는 직원이 확인(confirmed)하면 가림으로 보낸다
 *   4. 외부 AI    허용이면 원문, 가림이면 가명으로 바꾼 문장을 보낸다. 차단과 확인 전 경고는 부르지 않는다
 *   5. 복원       답의 가명을 원래 값으로 되돌린다
 *   6. 로그       모든 경로에서 정확히 한 줄. 원문과 값은 남기지 않는다
 *
 * 측정 처리(measure)는 외부 AI를 부르지 않고 탐지 결과와 가린 문장을 돌려준다. 측정 처리를 할지는 컨트롤러가 정한다.
 * 가명 대응표(MaskResult)는 이 메서드 안에서만 쓰고 버린다(AC-4.4).
 */
public class ChatGateway {

    private final InputValidator validator;
    private final DetectionEngine engine;
    private final ActionDecider decider;
    private final Masker masker;
    private final AnswerGenerator answerGenerator;
    private final GatewayLogger logger;
    private final boolean measurementMode;

    public ChatGateway(InputValidator validator, DetectionEngine engine, ActionDecider decider, Masker masker,
                       AnswerGenerator answerGenerator, GatewayLogger logger, boolean measurementMode) {
        this.validator = validator;
        this.engine = engine;
        this.decider = decider;
        this.masker = masker;
        this.answerGenerator = answerGenerator;
        this.logger = logger;
        this.measurementMode = measurementMode;
    }

    public boolean isMeasurementMode() {
        return measurementMode;
    }

    public GatewayResult handle(String prompt, boolean confirmed, boolean measure) {
        try {
            validator.validate(prompt);
        } catch (InvalidPromptException e) {
            logger.logError(prompt, "VALIDATION", e);
            throw e;
        }

        long t0 = System.nanoTime();
        List<Finding> findings = engine.detect(prompt);
        Action decided = decider.decide(findings);
        long detectMs = ms(t0);
        List<FindingCount> counts = FindingCount.of(findings);

        boolean confirmedWarn = decided == Action.WARN && confirmed;
        Action action = confirmedWarn ? Action.MASK : decided;

        if (measure) {
            long t1 = System.nanoTime();
            String masked = masker.mask(prompt, findings).maskedText();
            return logged(prompt, new GatewayResult(action, counts, null, confirmedWarn, masked, findings,
                    new GatewayResult.Timings(detectMs, ms(t1), null, null), 0, true));
        }
        if (action == Action.BLOCK || action == Action.WARN) {
            return logged(prompt, new GatewayResult(action, counts, null, false, null, null,
                    new GatewayResult.Timings(detectMs, null, null, null), 0, false));
        }
        if (action == Action.ALLOW) {
            long t2 = System.nanoTime();
            String answer = callAnswer(prompt, prompt);
            return logged(prompt, new GatewayResult(action, counts, answer, false, null, null,
                    new GatewayResult.Timings(detectMs, null, ms(t2), null), 0, false));
        }

        long t1 = System.nanoTime();
        MaskResult mask = masker.mask(prompt, findings);
        long maskMs = ms(t1);
        long t2 = System.nanoTime();
        String answer = callAnswer(mask.maskedText(), prompt);
        long answerMs = ms(t2);
        long t3 = System.nanoTime();
        RestoreResult restored = masker.restore(answer, mask);
        return logged(prompt, new GatewayResult(action, counts, restored.text(), confirmedWarn, null, null,
                new GatewayResult.Timings(detectMs, maskMs, answerMs, ms(t3)), restored.restored(), false));
    }

    /** 외부 AI에 text를 보낸다. 실패하면 원문(prompt)의 길이와 해시로 오류 로그를 남기고 그대로 알린다. */
    private String callAnswer(String text, String prompt) {
        try {
            return answerGenerator.generateAnswer(text);
        } catch (RuntimeException e) {
            logger.logError(prompt, "ANSWER", e);
            throw e;
        }
    }

    private GatewayResult logged(String prompt, GatewayResult result) {
        logger.logResult(prompt, result);
        return result;
    }

    private static long ms(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
