package com.promptgateway.judge.jev;

import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeException.Kind;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.QuestionSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * JevJudge의 실패 경로 시험(T6). 판정 호출이 어떤 식으로 실패해도 JudgeException으로 알려야
 * ChatGateway가 차단(fail-closed)으로 바꿀 수 있다. 재시도 대기 시간은 기록만 하고 실제로 기다리지 않는다.
 */
class JevJudgeFailureTest {

    static final String URL = JevJudgeTest.BASE_URL + "/v1/systemone";
    static final String FAKE_KEY = JevJudgeTest.FAKE_KEY;

    final QuestionSet questions = QuestionSet.fromClasspath("judge/questions.tsv");
    final List<Long> sleeps = new ArrayList<>();
    MockRestServiceServer server;
    JevJudge judge;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(JevJudgeTest.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        judge = new JevJudge(builder.build(), questions, JevJudgeTest.MODEL, FAKE_KEY,
                2, Duration.ofMillis(200), sleeps::add);
    }

    private static String ok() {
        return JevJudgeTest.response(JevJudgeTest.MODEL, "0.02", "0.02");
    }

    private Kind failureKind() {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> judge.judge("안녕하세요"));
        assertThat(t).isInstanceOf(JudgeException.class);
        return ((JudgeException) t).kind();
    }

    @Test
    @DisplayName("AC-6.1 타임아웃이면 TIMEOUT 실패이고 재시도하지 않는다")
    void timeoutIsFailureWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThat(failureKind()).isEqualTo(Kind.TIMEOUT);

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("연결할 수 없는 경우도 TIMEOUT 실패로 다룬다")
    void connectionFailureIsTimeoutKind() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withException(new IOException("Connection refused")));

        assertThat(failureKind()).isEqualTo(Kind.TIMEOUT);
    }

    @ParameterizedTest(name = "AC-6.2 {0}를 계속 받으면 2번 재시도한 뒤 RATE_LIMITED 실패이다")
    @ValueSource(ints = {429, 529})
    void rateLimitRetriesThenFails(int status) {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.RATE_LIMITED);

        server.verify();
        assertThat(sleeps).containsExactly(200L, 400L); // 지수로 늘어나는 대기: 200ms, 400ms
    }

    @Test
    @DisplayName("429 뒤에 재시도가 성공하면 정상 결과를 돌려준다")
    void rateLimitThenSuccess() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{}").contentType(MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess(ok(), MediaType.APPLICATION_JSON));

        JudgeResult result = judge.judge("안녕하세요");

        assertThat(result.scores()).hasSize(10);
        server.verify();
        assertThat(sleeps).containsExactly(200L);
    }

    @ParameterizedTest(name = "5xx({0})는 SERVER_ERROR 실패이고 재시도하지 않는다")
    @ValueSource(ints = {500, 502, 503})
    void serverErrorIsFailureWithoutRetry(int status) {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.SERVER_ERROR);

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @ParameterizedTest(name = "AC-6.3 {0}는 CONFIG_ERROR 실패이고 재시도하지 않는다")
    @ValueSource(ints = {401, 422})
    void configErrors(int status) {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).body("{}").contentType(MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.CONFIG_ERROR);

        server.verify();
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("400 max_tokens_exceeded는 INPUT_TOO_LONG 실패이다")
    void tooLongInput() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"code\":\"max_tokens_exceeded\"}}").contentType(MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.INPUT_TOO_LONG);
    }

    @Test
    @DisplayName("그 밖의 400은 요청 구성 오류이므로 CONFIG_ERROR 실패이다")
    void otherBadRequestIsConfigError() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"x\"}").contentType(MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.CONFIG_ERROR);
    }

    @ParameterizedTest(name = "AC-6.4 형식이 깨진 응답은 MALFORMED_RESPONSE 실패이다: {0}")
    @ValueSource(strings = {
            "not json",
            "{}",
            "{\"answers\":{}}",
            "{\"answers\":null}",
            "{\"answers\":{\"I1\":{\"noul\":null},\"I2a\":{\"noul\":0.1}}}",
            "{\"answers\":{\"I1\":{\"noul\":1.5}}}",
            "{\"answers\":{\"I1\":{\"noul\":-0.1}}}"
    })
    void malformedResponses(String body) {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(failureKind()).isEqualTo(Kind.MALFORMED_RESPONSE);
    }

    @Test
    @DisplayName("JSON이 아닌 형식(HTML 등)의 응답도 MALFORMED_RESPONSE 실패이다")
    void nonJsonResponse() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));

        assertThat(failureKind()).isEqualTo(Kind.MALFORMED_RESPONSE);
    }

    @Test
    @DisplayName("응답에서 질문 하나가 빠져도 JevJudge는 있는 것만 돌려준다 (누락 판정은 BlockDecider가 한다)")
    void missingQuestionIsLeftForBlockDecider() {
        String withoutF3 = ok().replace("\"F3\":{\"noul\":0.02},", "");
        server.expect(requestTo(URL)).andRespond(withSuccess(withoutF3, MediaType.APPLICATION_JSON));

        JudgeResult result = judge.judge("안녕하세요");

        assertThat(result.scores()).hasSize(9).doesNotContainKey("F3");
    }

    @Test
    @DisplayName("실패 메시지에 API 키나 Jev 응답 본문이 실리지 않는다")
    void failureMessageHasNoKeyOrUpstreamBody() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"secret\":\"UPSTREAM-BODY\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> judge.judge("안녕하세요"))
                .isInstanceOf(JudgeException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(FAKE_KEY).doesNotContain("UPSTREAM-BODY"));
    }
}
