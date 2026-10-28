package com.promptgateway.gateway;

import com.promptgateway.filter.PatternFilter;
import com.promptgateway.filter.PatternRule;
import com.promptgateway.judge.BlockDecider;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.QuestionSet;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.AnswerGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatGateway가 모든 경로에서 로그를 정확히 한 줄 남기는지 시험한다(T10, 설계 결정 S4, AC-7.1~7.3, AC-7.5).
 * 로그가 나가는 곳은 목록으로 바꿔 끼우고, 판정자와 응답 모델은 가짜를 쓴다.
 */
class ChatGatewayLoggingTest {

    static final String PROMPT = "연차 신청은 어떻게 하나요?";

    List<String> lines;
    Judge judge;
    AnswerGenerator answerer;
    List<PatternRule> rules;

    private static Map<String, Double> scores(String highId, double high) {
        Map<String, Double> m = new LinkedHashMap<>();
        QuestionSet.EXPECTED_IDS.forEach(id -> m.put(id, 0.02));
        if (highId != null) {
            m.put(highId, high);
        }
        return m;
    }

    @BeforeEach
    void setUp() {
        lines = new ArrayList<>();
        judge = prompt -> new JudgeResult(scores(null, 0), "jev-1.14.0", 5);
        answerer = prompt -> "답변";
        rules = List.of();
    }

    private ChatGateway gateway() {
        return new ChatGateway(new InputValidator(1000), new PatternFilter(rules), p -> judge.judge(p),
                new BlockDecider(QuestionSet.EXPECTED_IDS, 0.5), p -> answerer.generateAnswer(p),
                new GatewayLogger(false, lines::add));
    }

    @Test
    @DisplayName("통과하면 한 줄이고, 단계별 지연 시간 세 개가 모두 있다")
    void passLogsOneLineWithAllTimings() {
        gateway().handle(PROMPT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=PASS", "stage=NONE", "modelVersion=jev-1.14.0")
                .containsPattern("filterMs=\\d+").containsPattern("judgeMs=\\d+").containsPattern("answerMs=\\d+");
    }

    @Test
    @DisplayName("패턴으로 차단하면 한 줄이고, 판정 시간과 응답 시간은 없다")
    void patternBlockLogsOneLine() {
        rules = List.of(new PatternRule("P-01", "연차"));

        gateway().handle(PROMPT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=PATTERN", "patternId=P-01")
                .containsPattern("filterMs=\\d+").doesNotContain("judgeMs", "answerMs");
    }

    @Test
    @DisplayName("판정이 위반으로 판단해 차단하면 한 줄이고, 응답 시간은 없다")
    void judgeBlockLogsOneLine() {
        judge = prompt -> new JudgeResult(scores("F3", 0.9), "jev-1.14.0", 5);

        gateway().handle(PROMPT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=JUDGE", "F3:0.900")
                .containsPattern("judgeMs=\\d+").doesNotContain("answerMs");
    }

    @Test
    @DisplayName("판정 호출이 실패하면 한 줄이고, 실패 종류를 남긴다")
    void judgeFailureLogsOneLine() {
        judge = prompt -> {
            throw new JudgeException(JudgeException.Kind.TIMEOUT, "시간 초과");
        };

        gateway().handle(PROMPT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=BLOCK", "stage=JUDGE_FAILURE", "failureKind=TIMEOUT")
                .containsPattern("judgeMs=\\d+");
    }

    @Test
    @DisplayName("AC-7.5 입력 검증에 실패하면 예외는 그대로 던지고, 로그는 한 줄 남긴다")
    void validationFailureLogsAndRethrows() {
        assertThatThrownBy(() -> gateway().handle("  ")).isInstanceOf(InvalidPromptException.class);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=ERROR", "stage=VALIDATION", "errorKind=InvalidPromptException")
                .doesNotContain("filterMs", "judgeMs", "answerMs");
    }

    @Test
    @DisplayName("AC-7.5, AC-7.2 응답 모델이 실패하면 예외는 그대로 던지고, 로그에는 예외 메시지(키 포함)가 없다")
    void answerFailureLogsWithoutMessage() {
        answerer = prompt -> {
            throw new IllegalStateException("호출 주소 https://example.test/v1?key=SECRET-KEY-123");
        };

        assertThatThrownBy(() -> gateway().handle(PROMPT)).isInstanceOf(IllegalStateException.class);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=ERROR", "stage=ANSWER", "errorKind=IllegalStateException")
                .containsPattern("answerMs=\\d+").doesNotContain("SECRET-KEY-123");
    }

    @Test
    @DisplayName("AC-7.5 판정자가 예상하지 못한 예외를 던지면 그대로 던지고, 로그는 한 줄 남긴다")
    void unexpectedJudgeErrorLogsAndRethrows() {
        judge = prompt -> {
            throw new IllegalStateException("버그");
        };

        assertThatThrownBy(() -> gateway().handle(PROMPT)).isInstanceOf(IllegalStateException.class);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("decision=ERROR", "stage=JUDGE", "errorKind=IllegalStateException");
    }

    @Test
    @DisplayName("AC-7.3 기본 설정에서는 어떤 경로의 로그에도 프롬프트 원문이 없다")
    void noRawPromptInAnyPath() {
        gateway().handle(PROMPT);
        judge = prompt -> new JudgeResult(scores("F3", 0.9), "jev-1.14.0", 5);
        gateway().handle(PROMPT);

        assertThat(lines).hasSize(2).allSatisfy(l -> assertThat(l).doesNotContain(PROMPT));
    }
}
