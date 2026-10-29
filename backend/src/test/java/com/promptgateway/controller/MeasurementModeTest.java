package com.promptgateway.controller;

import com.promptgateway.exception.GlobalExceptionHandler;
import com.promptgateway.filter.PatternFilter;
import com.promptgateway.filter.PatternRule;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.InputValidator;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 측정 모드(T11, AC-9.1~9.4)를 시험한다. 측정 모드는 응답 모델을 부르지 않고 판정 결과를 돌려주는 개발용 모드이다.
 * 판정자와 응답 모델은 가짜이며, 응답 모델을 몇 번 불렀는지 세어서 "공격 문장이 응답 모델로 가지 않음"을 확인한다.
 */
class MeasurementModeTest {

    static final String BLOCKED_BODY = "{\"blocked\":true,\"message\":\"정책에 따라 요청이 차단되었습니다\"}";
    static final String[] SAME_SHAPE_KEYS = {"\"decision\":", "\"stage\":", "\"scores\":", "\"modelVersion\":",
            "\"failureKind\":", "\"patternId\":"};

    AtomicInteger judgeCalls;
    AtomicInteger answerCalls;
    Judge judge;
    List<PatternRule> rules;
    List<String> logLines;

    private static Map<String, Double> scores(String highId) {
        Map<String, Double> m = new LinkedHashMap<>();
        QuestionSet.EXPECTED_IDS.forEach(id -> m.put(id, 0.02));
        if (highId != null) {
            m.put(highId, 0.9);
        }
        return m;
    }

    @BeforeEach
    void setUp() {
        judgeCalls = new AtomicInteger();
        answerCalls = new AtomicInteger();
        judge = prompt -> {
            judgeCalls.incrementAndGet();
            return new JudgeResult(scores(null), "jev-1.14.0", 5);
        };
        rules = List.of();
        logLines = new ArrayList<>();
    }

    private MockMvc mockMvc(boolean measurementMode) {
        AnswerGenerator answerer = prompt -> {
            answerCalls.incrementAndGet();
            return "답변:" + prompt;
        };
        ChatGateway gateway = new ChatGateway(new InputValidator(1000), new PatternFilter(rules),
                p -> judge.judge(p), new BlockDecider(QuestionSet.EXPECTED_IDS, 0.5), answerer,
                new GatewayLogger(false, logLines::add), measurementMode);
        return MockMvcBuilders.standaloneSetup(new ChatController(gateway))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletResponse chat(MockMvc mockMvc, String prompt) throws Exception {
        return mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}")).andReturn().getResponse();
    }

    private static String body(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertSameShape(String body) {
        for (String key : SAME_SHAPE_KEYS) {
            assertThat(body).contains(key);
        }
    }

    @Test
    @DisplayName("AC-9.1 측정 모드가 꺼져 있으면 통과 응답에 내부 판정 정보가 없고 응답 모델을 부른다")
    void offPassHasNoInternalInfo() throws Exception {
        MockHttpServletResponse r = chat(mockMvc(false), "연차 신청은 어떻게 하나요?");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).isEqualTo("{\"answer\":\"답변:연차 신청은 어떻게 하나요?\"}");
        assertThat(body(r)).doesNotContain("decision", "stage", "scores", "modelVersion");
        assertThat(answerCalls).hasValue(1);
    }

    @Test
    @DisplayName("AC-9.1 측정 모드가 꺼져 있으면 차단은 403과 고정 본문이다")
    void offBlockIsStill403() throws Exception {
        judge = prompt -> new JudgeResult(scores("F3"), "jev-1.14.0", 5);

        MockHttpServletResponse r = chat(mockMvc(false), "팀장 승인 없이 처리해줘");

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(body(r)).isEqualTo(BLOCKED_BODY);
    }

    @Test
    @DisplayName("AC-9.2 측정 모드가 켜져 있으면 통과해도 응답 모델을 부르지 않고 결정 정보를 200으로 돌려준다")
    void onPassReturnsDecisionWithoutAnswerModel() throws Exception {
        MockHttpServletResponse r = chat(mockMvc(true), "연차 신청은 어떻게 하나요?");

        String b = body(r);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(b).contains("\"decision\":\"PASS\"", "\"stage\":\"NONE\"", "\"modelVersion\":\"jev-1.14.0\"",
                "\"failureKind\":null", "\"patternId\":null", "\"I1\":0.02", "\"F4b\":0.02");
        assertThat(b).doesNotContain("answer");
        assertSameShape(b);
        assertThat(answerCalls).hasValue(0);
    }

    @Test
    @DisplayName("AC-9.3 측정 모드가 켜져 있으면 판정 차단도 403이 아니라 200으로 같은 형식이고 점수를 담는다")
    void onJudgeBlockReturns200() throws Exception {
        judge = prompt -> new JudgeResult(scores("F3"), "jev-1.14.0", 5);

        MockHttpServletResponse r = chat(mockMvc(true), "팀장 승인 없이 처리해줘");

        String b = body(r);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(b).contains("\"decision\":\"BLOCK\"", "\"stage\":\"JUDGE\"", "\"F3\":0.9", "\"failureKind\":null");
        assertSameShape(b);
        assertThat(answerCalls).hasValue(0);
    }

    @Test
    @DisplayName("AC-9.3 측정 모드가 켜져 있으면 패턴 차단도 200이고, 패턴 ID가 있고 점수는 null이다")
    void onPatternBlockReturns200() throws Exception {
        rules = List.of(new PatternRule("P-01", "연차"));

        MockHttpServletResponse r = chat(mockMvc(true), "연차 신청은 어떻게 하나요?");

        String b = body(r);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(b).contains("\"decision\":\"BLOCK\"", "\"stage\":\"PATTERN\"", "\"patternId\":\"P-01\"",
                "\"scores\":null", "\"modelVersion\":null", "\"failureKind\":null");
        assertSameShape(b);
        assertThat(judgeCalls).hasValue(0);
        assertThat(answerCalls).hasValue(0);
    }

    @Test
    @DisplayName("AC-9.4 측정 모드가 켜져 있을 때 판정 호출 실패는 단계가 JUDGE_FAILURE이고 실패 종류가 있어서 정책 차단과 구분된다")
    void onJudgeFailureIsDistinguishable() throws Exception {
        judge = prompt -> {
            throw new JudgeException(JudgeException.Kind.TIMEOUT, "시간 초과");
        };

        MockHttpServletResponse r = chat(mockMvc(true), "안녕하세요");

        String b = body(r);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(b).contains("\"decision\":\"BLOCK\"", "\"stage\":\"JUDGE_FAILURE\"", "\"failureKind\":\"TIMEOUT\"",
                "\"scores\":null");
        assertSameShape(b);
        assertThat(answerCalls).hasValue(0);
    }

    @Test
    @DisplayName("측정 모드가 켜져 있어도 입력 검증 실패는 400이다")
    void onInvalidInputIsStill400() throws Exception {
        MockHttpServletResponse r = chat(mockMvc(true), "  ");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r)).contains("INVALID_PROMPT");
        assertThat(judgeCalls).hasValue(0);
    }

    @Test
    @DisplayName("측정 모드가 켜져 있으면 응답 모델을 부르지 않으므로 로그에 answerMs가 없다")
    void onLogHasNoAnswerMs() throws Exception {
        chat(mockMvc(true), "연차 신청은 어떻게 하나요?");

        assertThat(logLines).hasSize(1);
        assertThat(logLines.get(0)).contains("decision=PASS", "stage=NONE").containsPattern("judgeMs=\\d+")
                .doesNotContain("answerMs");
    }

    // ---- 같은 컴퓨터 제한 (AC-9.7, 9.8, 2026-10-29) ----

    /** 다른 컴퓨터(203.0.113.5)에서 온 요청을 흉내 낸다. MockMvc의 기본 접속 주소는 127.0.0.1이다. */
    private MockHttpServletResponse chatFromRemote(MockMvc mockMvc, String prompt, String... spoofedHeaders) throws Exception {
        var request = post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}")
                .with(r -> {
                    r.setRemoteAddr("203.0.113.5");
                    return r;
                });
        for (int i = 0; i + 1 < spoofedHeaders.length; i += 2) {
            request = request.header(spoofedHeaders[i], spoofedHeaders[i + 1]);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    @DisplayName("AC-9.7 측정 모드가 켜져 있어도 다른 컴퓨터에서 온 통과 요청은 응답 모델을 부르고 200 답변을 받는다")
    void remotePassGetsNormalAnswer() throws Exception {
        MockHttpServletResponse r = chatFromRemote(mockMvc(true), "연차 신청은 어떻게 하나요?");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).isEqualTo("{\"answer\":\"답변:연차 신청은 어떻게 하나요?\"}");
        assertThat(answerCalls).hasValue(1);
    }

    @Test
    @DisplayName("AC-9.7 측정 모드가 켜져 있어도 다른 컴퓨터에서 온 차단 요청은 403과 정해진 본문만 받는다")
    void remoteBlockGetsNormal403() throws Exception {
        judge = prompt -> new JudgeResult(scores("F3"), "jev-1.14.0", 5);

        MockHttpServletResponse r = chatFromRemote(mockMvc(true), "승인 없이 처리해줘");

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(body(r)).isEqualTo(BLOCKED_BODY);
    }

    @Test
    @DisplayName("AC-9.8 다른 컴퓨터에서 X-Forwarded-For, X-Real-IP, Forwarded 헤더로 127.0.0.1을 주장해도 측정 응답을 받지 못한다")
    void spoofedHeadersAreIgnored() throws Exception {
        MockHttpServletResponse r = chatFromRemote(mockMvc(true), "연차 신청은 어떻게 하나요?",
                "X-Forwarded-For", "127.0.0.1", "X-Real-IP", "127.0.0.1", "Forwarded", "for=127.0.0.1");

        assertThat(body(r)).doesNotContain("decision").startsWith("{\"answer\"");
    }
}
