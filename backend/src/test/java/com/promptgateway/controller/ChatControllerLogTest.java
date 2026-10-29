package com.promptgateway.controller;

import ch.qos.logback.classic.Level;
import com.promptgateway.exception.GlobalExceptionHandler;
import com.promptgateway.filter.PatternFilter;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.InputValidator;
import com.promptgateway.judge.BlockDecider;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.QuestionSet;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.service.GeminiService;
import com.promptgateway.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 요청이 들어와서 응답이 나갈 때까지 남은 모든 로그(INFO 이상)에 API 키가 없는지 시험한다(T10, AC-7.2).
 * 응답 모델은 실제 GeminiService에 가짜 키를 넣고 MockRestServiceServer로 오류를 흉내 낸다. 키는 URL 쿼리(?key=)로 가므로
 * 예외 메시지나 스택을 통해 로그에 섞일 수 있는 경로를 모두 지나가게 한다.
 */
class ChatControllerLogTest {

    static final String FAKE_KEY = "TEST-KEY-LOG-54321";
    static final String GEMINI_URL = "https://gemini.test/v1beta/models/test-model:generateContent?key=" + FAKE_KEY;
    static final String OK_BODY = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"안녕하세요\"}]}}]}";

    MockRestServiceServer server;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        GeminiService service = new GeminiService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", FAKE_KEY);
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "baseUrl", "https://gemini.test/v1beta/models");
        Judge alwaysPass = prompt -> {
            Map<String, Double> low = new LinkedHashMap<>();
            QuestionSet.EXPECTED_IDS.forEach(id -> low.put(id, 0.01));
            return new JudgeResult(low, "jev-1.14.0", 1);
        };
        ChatGateway gateway = new ChatGateway(new InputValidator(1000), new PatternFilter(List.of()), alwaysPass,
                new BlockDecider(QuestionSet.EXPECTED_IDS, 0.5), service, new GatewayLogger(false));
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(gateway))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void chat(String json) throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("AC-7.2 정상, 401, 429, 400, 500, 연결 오류, 깨진 응답의 어느 경로에서도 로그에 API 키 값이 없다")
    void noApiKeyInAnyLog() throws Exception {
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"key " + FAKE_KEY + "\"}"));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body(GEMINI_URL));
        server.expect(requestTo(GEMINI_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(GEMINI_URL));
        server.expect(requestTo(GEMINI_URL)).andRespond(withException(new IOException("connect to " + GEMINI_URL + " refused")));
        server.expect(requestTo(GEMINI_URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        try (LogCapture logs = LogCapture.root()) {
            for (int i = 0; i < 7; i++) {
                chat("{\"prompt\":\"안녕\"}");
            }

            assertThat(logs.messages()).isNotEmpty();
            assertThat(logs.fullTextAtLeast(Level.INFO)).doesNotContain(FAKE_KEY);
        }
        server.verify();
    }
}
