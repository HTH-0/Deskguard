package com.promptgateway.log;

import ch.qos.logback.classic.Level;
import com.promptgateway.gateway.GatewayResult;
import com.promptgateway.support.LogCapture;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 감사 로그(AC-7.1, 7.2). 원문, 탐지한 값, 가명 대응표가 로그에 없는지 실제 게이트웨이 흐름으로 확인한다. */
class GatewayLoggerTest {

    static final String SENSITIVE = "김민수 010-2345-6789, minsu@mail.example, 한결물산 / DB_PASSWORD=Qw7!zLp92x";

    @Test
    @DisplayName("AC-7.1 한 줄에 요청 ID, 처리, 범주별 개수, 원문 길이와 해시, 단계별 지연이 있다")
    void lineHasRequiredFields() {
        List<String> lines = new ArrayList<>();
        TestGateways.gateway(p -> "답", new GatewayLogger(lines::add), false).handle("김민수 010-2345-6789 정리", false, false);

        assertThat(lines).singleElement().asString()
                .containsPattern("requestId=[0-9a-f-]{36}")
                .contains("action=MASK", "findings=D1.PHONE:1", "restored=0", "promptLength=",
                        "promptHash=" + GatewayLogger.hash("김민수 010-2345-6789 정리"))
                .containsPattern("detectMs=\\d+").containsPattern("maskMs=\\d+")
                .containsPattern("answerMs=\\d+").containsPattern("restoreMs=\\d+");
    }

    @Test
    @DisplayName("AC-7.2 어떤 처리든 로그에 원문, 탐지한 값, 가명이 없다")
    void noSensitiveContentInLog() {
        List<String> lines = new ArrayList<>();
        var gateway = TestGateways.gateway(p -> "답: " + p, new GatewayLogger(lines::add), false);

        gateway.handle(SENSITIVE, false, false);
        gateway.handle("김민수 010-2345-6789, 한결물산", false, false);

        String all = String.join("\n", lines);
        assertThat(all).doesNotContain("김민수", "010-2345-6789", "minsu@mail.example", "한결물산", "Qw7!zLp92x", "[전화1]", "[고객사1]");
    }

    @Test
    @DisplayName("AC-7.2 오류 줄에는 예외 메시지를 싣지 않는다")
    void errorLineHasNoMessage() {
        List<String> lines = new ArrayList<>();
        new GatewayLogger(lines::add).logError("x", "ANSWER", new IllegalStateException("key=SECRET-999"));

        assertThat(lines).singleElement().asString().contains("action=ERROR", "stage=ANSWER", "errorKind=IllegalStateException")
                .doesNotContain("SECRET-999");
    }

    @Test
    @DisplayName("SLF4J 수준: 허용과 가림은 INFO, 경고와 차단과 오류는 WARN(보안 이벤트)")
    void levels() {
        try (LogCapture logs = LogCapture.of(GatewayLogger.class)) {
            var gateway = TestGateways.gateway(p -> "답", new GatewayLogger(), false);
            gateway.handle("회의록 요약", false, false);
            gateway.handle("010-2345-6789", false, false);
            gateway.handle(TestGateways.phoneList(20), false, false);
            gateway.handle("password=Qw7!zLp92x", false, false);

            assertThat(logs.events()).extracting(e -> e.getLevel())
                    .containsExactly(Level.INFO, Level.INFO, Level.WARN, Level.WARN);
        }
    }

    @Test
    @DisplayName("해시는 UTF-8 SHA-256의 앞 16자리이다")
    void hash() {
        assertThat(GatewayLogger.hash("abc")).isEqualTo("ba7816bf8f01cfea");
    }

    @Test
    @DisplayName("측정 처리에서도 로그에 가린 문장이나 값이 없다")
    void measurementLogHasNoValues() {
        List<String> lines = new ArrayList<>();
        GatewayResult r = TestGateways.gateway(p -> "답", new GatewayLogger(lines::add), true)
                .handle("김민수 010-2345-6789", false, true);

        assertThat(r.maskedPrompt()).isNotNull();
        assertThat(lines).singleElement().asString().contains("measure=true").doesNotContain("010-2345-6789", "[전화1]");
    }
}
