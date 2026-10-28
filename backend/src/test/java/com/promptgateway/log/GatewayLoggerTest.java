package com.promptgateway.log;

import com.promptgateway.gateway.GatewayResult;
import com.promptgateway.gateway.InvalidPromptException;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.QuestionSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GatewayLogger가 만드는 로그 한 줄의 내용을 시험한다(T10, AC-7.1~7.4).
 * 로그가 나가는 곳(sink)을 목록으로 바꿔 끼워서, 줄 하나하나를 문자열로 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class GatewayLoggerTest {

    static final String PROMPT = "연차 신청은 어떻게 하나요?";

    List<String> lines = new ArrayList<>();
    GatewayLogger logger = new GatewayLogger(false, lines::add);

    private static JudgeResult judgeResult(String highId, double high) {
        Map<String, Double> scores = new LinkedHashMap<>();
        QuestionSet.EXPECTED_IDS.forEach(id -> scores.put(id, 0.02));
        if (highId != null) {
            scores.put(highId, high);
        }
        return new JudgeResult(scores, "jev-1.14.0", 12);
    }

    @Test
    @DisplayName("AC-7.1 통과한 요청의 한 줄에 결정, 단계, 질문별 확률, 모델 버전, 단계별 지연 시간이 있다")
    void passedLineHasAllFields() {
        logger.logResult(PROMPT, GatewayResult.passed(judgeResult(null, 0), "답변"),
                new GatewayLogger.Timings(1L, 2L, 3L));

        assertThat(lines).hasSize(1);
        String line = lines.get(0);
        assertThat(line).contains("decision=PASS", "stage=NONE", "modelVersion=jev-1.14.0",
                "filterMs=1", "judgeMs=2", "answerMs=3");
        QuestionSet.EXPECTED_IDS.forEach(id -> assertThat(line).contains(id + ":0.020"));
        assertThat(line).containsPattern("requestId=[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(line).doesNotContain("\n");
    }

    @Test
    @DisplayName("판정이 위반으로 판단해 차단한 줄은 decision=BLOCK, stage=JUDGE이고 점수를 남긴다")
    void judgeBlockLine() {
        logger.logResult(PROMPT, GatewayResult.blockedByJudge(judgeResult("F3", 0.9)),
                new GatewayLogger.Timings(1L, 2L, null));

        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=JUDGE", "F3:0.900", "judgeMs=2")
                .doesNotContain("answerMs", "patternId", "failureKind");
    }

    @Test
    @DisplayName("패턴 차단 줄은 patternId가 있고, 판정을 하지 않았으므로 점수와 판정 시간이 없다")
    void patternBlockLine() {
        logger.logResult(PROMPT, GatewayResult.blockedByPattern("P-01"), new GatewayLogger.Timings(1L, null, null));

        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=PATTERN", "patternId=P-01", "filterMs=1")
                .doesNotContain("scores=", "modelVersion", "judgeMs", "answerMs");
    }

    @Test
    @DisplayName("판정 실패 차단 줄은 failureKind가 있고 점수가 없다")
    void judgeFailureLine() {
        logger.logResult(PROMPT, GatewayResult.blockedByJudgeFailure(JudgeException.Kind.TIMEOUT),
                new GatewayLogger.Timings(1L, 3000L, null));

        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=JUDGE_FAILURE", "failureKind=TIMEOUT", "judgeMs=3000")
                .doesNotContain("scores=", "modelVersion");
    }

    @Test
    @DisplayName("AC-7.3 원문 저장이 꺼져 있으면 원문은 없고 길이와 해시만 있다")
    void defaultHasNoRawPrompt() {
        logger.logResult(PROMPT, GatewayResult.blockedByPattern("P-01"), new GatewayLogger.Timings(1L, null, null));

        String line = lines.get(0);
        assertThat(line).doesNotContain(PROMPT).doesNotContain("prompt=");
        assertThat(line).contains("promptLength=" + PROMPT.length(), "promptHash=" + GatewayLogger.hash(PROMPT));
    }

    @Test
    @DisplayName("AC-7.4 원문 저장을 켜면 원문이 한 줄 안에 기록된다(따옴표와 줄바꿈은 이스케이프한다)")
    void rawPromptWhenEnabled() {
        GatewayLogger raw = new GatewayLogger(true, lines::add);

        raw.logResult("a\"b\nc", GatewayResult.blockedByPattern("P-01"), new GatewayLogger.Timings(1L, null, null));

        assertThat(lines.get(0)).contains("prompt=\"a\\\"b\\nc\"").doesNotContain("\n");
    }

    @Test
    @DisplayName("해시는 SHA-256의 앞 16자리(16진수)이다")
    void hashIsFirst16HexOfSha256() {
        // SHA-256("abc") = ba7816bf 8f01cfea 414140de 5dae2223 b00361a3 96177a9c b410ff61 f20015ad
        assertThat(GatewayLogger.hash("abc")).isEqualTo("ba7816bf8f01cfea");
    }

    @Test
    @DisplayName("AC-7.5 입력 검증 실패는 decision=ERROR, stage=VALIDATION으로 한 줄 남긴다")
    void validationErrorLine() {
        logger.logError(" ", "VALIDATION", new InvalidPromptException("prompt는 비어 있을 수 없습니다."),
                GatewayLogger.Timings.NONE);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=ERROR", "stage=VALIDATION", "errorKind=InvalidPromptException",
                "promptLength=1");
    }

    @Test
    @DisplayName("AC-7.2 오류 줄에는 예외 메시지를 싣지 않는다(메시지에 키가 들어 있어도 로그에 나오지 않는다)")
    void errorLineNeverCarriesExceptionMessage() {
        RuntimeException leaky = new RuntimeException("https://example.test/v1?key=SECRET-KEY-123 호출 실패");

        logger.logError(PROMPT, "ANSWER", leaky, new GatewayLogger.Timings(1L, 2L, 3L));

        assertThat(lines.get(0)).contains("decision=ERROR", "stage=ANSWER", "errorKind=RuntimeException")
                .doesNotContain("SECRET-KEY-123", "example.test");
    }

    @Test
    @DisplayName("prompt가 null이어도 로그가 실패하지 않고 길이는 0이다")
    void nullPromptIsSafe() {
        logger.logError(null, "VALIDATION", new InvalidPromptException("없음"), GatewayLogger.Timings.NONE);

        assertThat(lines.get(0)).contains("promptLength=0").doesNotContain("promptHash");
    }

    @Test
    @DisplayName("기본 출력은 SLF4J 로그이다")
    void defaultSinkWritesToLog(CapturedOutput output) {
        new GatewayLogger(false).logResult(PROMPT, GatewayResult.blockedByPattern("P-01"),
                new GatewayLogger.Timings(1L, null, null));

        assertThat(output.getOut()).contains("decision=BLOCK", "stage=PATTERN");
    }
}
