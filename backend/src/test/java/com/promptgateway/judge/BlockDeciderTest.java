package com.promptgateway.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockDeciderTest {

    static final List<String> IDS = List.of("I1", "I2a", "I2b", "I3", "I4", "F1", "F2", "F3", "F4a", "F4b");

    private static Map<String, Double> allAt(double value) {
        Map<String, Double> scores = new LinkedHashMap<>();
        IDS.forEach(id -> scores.put(id, value));
        return scores;
    }

    private final BlockDecider decider = new BlockDecider(IDS, 0.5);

    @Test
    @DisplayName("AC-3.6 확률이 정확히 임계값(0.5)이면 차단한다")
    void blocksAtExactlyThreshold() {
        Map<String, Double> scores = allAt(0.01);
        scores.put("F1", 0.5);

        assertThat(decider.shouldBlock(scores)).isTrue();
    }

    @Test
    @DisplayName("AC-3.7 모든 질문이 0.49이면 통과시킨다")
    void passesJustBelowThreshold() {
        assertThat(decider.shouldBlock(allAt(0.49))).isFalse();
    }

    @ParameterizedTest(name = "AC-3.10 {0}만 높아도 차단한다")
    @ValueSource(strings = {"I1", "I2a", "I2b", "I3", "I4", "F1", "F2", "F3", "F4a", "F4b"})
    void blocksWhenOnlyOneQuestionIsHigh(String id) {
        Map<String, Double> scores = allAt(0.01);
        scores.put(id, 0.9);

        assertThat(decider.shouldBlock(scores)).isTrue();
    }

    @Test
    @DisplayName("모든 질문이 0이면 통과시킨다")
    void passesWhenAllZero() {
        assertThat(decider.shouldBlock(allAt(0.0))).isFalse();
    }

    @Test
    @DisplayName("AC-3.5 질문 하나가 응답에서 빠지면 판정 실패로 본다")
    void missingQuestionIsMalformed() {
        Map<String, Double> scores = allAt(0.01);
        scores.remove("F3");

        assertThatThrownBy(() -> decider.shouldBlock(scores))
                .isInstanceOf(JudgeException.class)
                .extracting(e -> ((JudgeException) e).kind())
                .isEqualTo(JudgeException.Kind.MALFORMED_RESPONSE);
    }

    @Test
    @DisplayName("임계값은 생성할 때 정한 값을 따른다 (0.7이면 0.6은 통과)")
    void followsConfiguredThreshold() {
        BlockDecider lenient = new BlockDecider(IDS, 0.7);
        Map<String, Double> scores = allAt(0.01);
        scores.put("I1", 0.6);

        assertThat(lenient.shouldBlock(scores)).isFalse();
    }
}
