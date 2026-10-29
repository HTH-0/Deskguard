package com.promptgateway.controller;

import com.promptgateway.exception.GlobalExceptionHandler;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.GeminiService;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 스텝 7 특성화 테스트: 3단계 프록시 골격의 현재 /api/chat 동작을 있는 그대로 고정한다.
 * "현재 동작이 옳은가"를 판단하지 않는다. 스텝 8에서 동작을 바꾸면 이 테스트가 먼저 깨져서
 * 무엇이 바뀌는지 알려준다. 바뀌는 것이 의도라면 설계서와 명세서를 먼저 고치고 이 테스트를 고친다.
 *
 * 외부 호출은 없다. 응답 모델(Gemini)은 MockRestServiceServer로 대체하고 키는 가짜 값을 쓴다.
 *
 * 스텝 8(T9)에서 의도적으로 바뀐 동작은 설계 결정 D12, D13에 따라 테스트를 고쳤다:
 * 입력 검증(빈 문자열, 공백, 1,001자는 400), 깨진 요청 본문은 400, 오류 응답의 message는 고정 문구.
 */
class ChatControllerCharacterizationTest {

    static final String FAKE_KEY = "TEST-KEY-12345";
    static final String GEMINI_URL = "https://gemini.test/v1beta/models/test-model:generateContent?key=" + FAKE_KEY;
    static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"안녕하세요\"}]}}]}";

    MockRestServiceServer server;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        GeminiService service = new GeminiService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", FAKE_KEY);
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "baseUrl", "https://gemini.test/v1beta/models");
        // 운영 게이트웨이와 같은 조립(탐지, 결정, 가림)에 응답 모델만 가짜 키의 GeminiService를 쓴다.
        // 이 시험은 "안녕"처럼 탐지가 없는 문장으로 외부 AI 쪽 동작(호출과 오류 변환)만 본다.
        ChatGateway gateway = TestGateways.gateway(service, new GatewayLogger(line -> { }), false);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(gateway))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletResponse chat(String jsonBody) throws Exception {
        return mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andReturn().getResponse();
    }

    private static String body(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    private void geminiReturns(HttpStatus status, String json) {
        server.expect(requestTo(GEMINI_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).body(json).contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("정상 요청은 200과 answer를 돌려준다")
    void ok() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).isEqualTo("{\"action\":\"ALLOW\",\"answer\":\"안녕하세요\",\"findings\":[]}");
        server.verify();
    }

    @Test
    @DisplayName("Gemini 키는 URL 쿼리(?key=)로 전달된다 (위험으로 기록, 설계서 7.2)")
    void keyIsSentAsUrlQuery() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        chat("{\"prompt\":\"안녕\"}");

        server.verify(); // GEMINI_URL에 ?key=가 포함되어 있어야 통과한다
    }

    @Test
    @DisplayName("[T9에서 바뀜] 빈 문자열은 400이고 Gemini를 호출하지 않는다 (FR-1)")
    void emptyPromptIsRejected() throws Exception {
        MockHttpServletResponse r = chat("{\"prompt\":\"\"}");

        assertThat(r.getStatus()).isEqualTo(400);
        server.verify(); // 기대한 요청이 없으므로 Gemini 호출이 있었다면 여기서 실패한다
    }

    @Test
    @DisplayName("[T9에서 바뀜] 공백뿐인 문자열은 400이고 Gemini를 호출하지 않는다 (FR-1)")
    void blankPromptIsRejected() throws Exception {
        MockHttpServletResponse r = chat("{\"prompt\":\"   \"}");

        assertThat(r.getStatus()).isEqualTo(400);
        server.verify();
    }

    @Test
    @DisplayName("[T9에서 바뀜, 명세 v2 S1에서 다시 바뀜] 4,001자 입력은 400이고 Gemini를 호출하지 않는다 (FR-1, 최대 4,000자)")
    void tooLongPromptIsRejected() throws Exception {
        MockHttpServletResponse r = chat("{\"prompt\":\"" + "가".repeat(4001) + "\"}");

        assertThat(r.getStatus()).isEqualTo(400);
        server.verify();
    }

    @Test
    @DisplayName("Gemini 401은 401 UPSTREAM_UNAUTHORIZED로 바뀐다")
    void upstream401() throws Exception {
        geminiReturns(HttpStatus.UNAUTHORIZED, "{\"error\":\"bad key\"}");

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(body(r)).contains("\"error\":\"UPSTREAM_UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("Gemini 429는 429 UPSTREAM_RATE_LIMITED로 바뀐다")
    void upstream429() throws Exception {
        geminiReturns(HttpStatus.TOO_MANY_REQUESTS, "{}");

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(429);
        assertThat(body(r)).contains("\"error\":\"UPSTREAM_RATE_LIMITED\"");
    }

    @Test
    @DisplayName("그 밖의 Gemini 4xx는 같은 상태 코드의 UPSTREAM_CLIENT_ERROR이다 ([T9에서 바뀜] 메시지에 Gemini 응답 본문을 싣지 않는다)")
    void upstream400() throws Exception {
        geminiReturns(HttpStatus.BAD_REQUEST, "{\"error\":\"bad request\"}");

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r)).contains("\"error\":\"UPSTREAM_CLIENT_ERROR\"");
        assertThat(body(r)).doesNotContain("bad request").doesNotContain("gemini.test"); // 외부 응답과 주소를 싣지 않는다(D12)
    }

    @Test
    @DisplayName("Gemini 5xx는 전용 처리기가 없어 500 INTERNAL_ERROR로 바뀐다")
    void upstream500() throws Exception {
        geminiReturns(HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":\"boom\"}");

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(body(r)).contains("\"error\":\"INTERNAL_ERROR\"");
    }

    @Test
    @DisplayName("연결 오류나 타임아웃은 504 UPSTREAM_TIMEOUT으로 바뀐다")
    void upstreamTimeout() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withException(new IOException("Read timed out")));

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(504);
        assertThat(body(r)).contains("\"error\":\"UPSTREAM_TIMEOUT\"");
    }

    @Test
    @DisplayName("Gemini 응답에 후보가 없으면 500 INTERNAL_ERROR이다")
    void emptyCandidates() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockHttpServletResponse r = chat("{\"prompt\":\"안녕\"}");

        assertThat(r.getStatus()).isEqualTo(500);
        assertThat(body(r)).contains("\"error\":\"INTERNAL_ERROR\"");
    }

    @Test
    @DisplayName("깨진 응답이나 JSON이 아닌 응답도 500 INTERNAL_ERROR이다")
    void malformedUpstreamResponse() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThat(chat("{\"prompt\":\"안녕\"}").getStatus()).isEqualTo(500);
        server.reset();

        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("<html>", MediaType.TEXT_HTML));
        assertThat(chat("{\"prompt\":\"안녕\"}").getStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("점검 O1: 어떤 오류 응답에도 Gemini 키가 실리지 않는다 (확인한 경우에 한함)")
    void keyNeverAppearsInErrorResponses() throws Exception {
        HttpStatus[] statuses = {HttpStatus.UNAUTHORIZED, HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.BAD_REQUEST, HttpStatus.INTERNAL_SERVER_ERROR};
        for (HttpStatus s : statuses) {
            geminiReturns(s, "{\"error\":\"x\"}");
            assertThat(body(chat("{\"prompt\":\"안녕\"}"))).doesNotContain(FAKE_KEY);
            server.reset();
        }
        server.expect(requestTo(GEMINI_URL)).andRespond(withException(new IOException("Read timed out")));
        assertThat(body(chat("{\"prompt\":\"안녕\"}"))).doesNotContain(FAKE_KEY);
        server.reset();

        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThat(body(chat("{\"prompt\":\"안녕\"}"))).doesNotContain(FAKE_KEY);
    }

    @Test
    @DisplayName("[T9에서 바뀜] 요청 본문이 깨진 JSON이면 500이 아니라 400이다 (D13)")
    void malformedRequestBody() throws Exception {
        MockHttpServletResponse r = chat("{not json");

        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(body(r)).contains("\"error\":\"INVALID_REQUEST\"");
    }
}
