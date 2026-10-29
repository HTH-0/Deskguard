package com.promptgateway.controller;

import com.promptgateway.exception.GlobalExceptionHandler;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 컨트롤러 통합 시험(명세 v2 FR-6, FR-9, design.md 5장): 처리별 상태 코드와 본문, 측정 모드의 같은 컴퓨터 제한.
 * 외부 AI는 받은 문장을 기록하는 가짜이다.
 */
class ChatControllerTest {

    List<String> sentToAi;

    private MockMvc mvc(boolean measurementMode) {
        sentToAi = new ArrayList<>();
        ChatGateway gateway = TestGateways.gateway(p -> {
            sentToAi.add(p);
            return "답: " + p;
        }, new GatewayLogger(line -> { }), measurementMode);
        return MockMvcBuilders.standaloneSetup(new ChatController(gateway)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mvc(false);
    }

    private static String json(String prompt, boolean confirmed) {
        return "{\"prompt\":\"" + prompt.replace("\n", "\\n") + "\",\"confirmed\":" + confirmed + "}";
    }

    private MockHttpServletResponse chat(MockMvc m, String body, String remoteAddr) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body);
        if (remoteAddr != null) {
            req = req.with(r -> {
                r.setRemoteAddr(remoteAddr);
                return r;
            });
        }
        return m.perform(req).andReturn().getResponse();
    }

    private MockHttpServletResponse chat(String prompt) throws Exception {
        return chat(mockMvc, "{\"prompt\":\"" + prompt + "\"}", null);
    }

    private static String body(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("허용: 200, action=ALLOW, 답, 빈 findings")
    void allow() throws Exception {
        MockHttpServletResponse r = chat("회의록 요약해 줘");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).isEqualTo("{\"action\":\"ALLOW\",\"answer\":\"답: 회의록 요약해 줘\",\"findings\":[]}");
    }

    @Test
    @DisplayName("AC-6.1 가림: 200, 복원된 답, 범주별 개수. 응답에 가명과 값의 위치는 없다")
    void mask() throws Exception {
        MockHttpServletResponse r = chat("김민수 010-2345-6789 정리");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).isEqualTo("{\"action\":\"MASK\",\"answer\":\"답: 김민수 010-2345-6789 정리\","
                + "\"findings\":[{\"category\":\"D1\",\"item\":\"PHONE\",\"count\":1}]}");
        assertThat(sentToAi).containsExactly("김민수 [전화1] 정리");
    }

    @Test
    @DisplayName("AC-6.2 경고: 200과 확인 요청. confirmed=true로 다시 보내면 가림으로 보낸다")
    void warnFlow() throws Exception {
        String list = TestGateways.phoneList(20);

        MockHttpServletResponse first = chat(mockMvc, json(list, false), null);
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(body(first)).contains("\"action\":\"WARN\"", "\"confirmRequired\":true", "\"count\":20").doesNotContain("010-2000");
        assertThat(sentToAi).isEmpty();

        MockHttpServletResponse second = chat(mockMvc, json(list, true), null);
        assertThat(body(second)).contains("\"action\":\"MASK\"", "010-2000-3000");
        assertThat(sentToAi).singleElement().asString().doesNotContain("010-2000-3000");
    }

    @Test
    @DisplayName("AC-6.3 차단: 403, 범주와 개수와 안내만. 탐지한 값은 응답에 없다")
    void block() throws Exception {
        MockHttpServletResponse r = chat("DB_PASSWORD=Qw7!zLp92x 왜 안 돼?");

        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(body(r)).contains("\"action\":\"BLOCK\"", "\"item\":\"PASSWORD\"", "\"message\":")
                .doesNotContain("Qw7!zLp92x");
        assertThat(sentToAi).isEmpty();
    }

    @Test
    @DisplayName("AC-1.1, 1.2 빈 입력과 4,000자 초과는 400이다")
    void validation() throws Exception {
        assertThat(chat("   ").getStatus()).isEqualTo(400);
        assertThat(chat("가".repeat(4001)).getStatus()).isEqualTo(400);
        assertThat(chat("가".repeat(4000)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("AC-9.1 측정 모드가 꺼져 있으면 응답에 가린 문장과 위치가 없다")
    void measurementOffHidesDetails() throws Exception {
        assertThat(body(chat("010-2345-6789"))).doesNotContain("maskedPrompt", "start", "[전화1]");
    }

    @Test
    @DisplayName("AC-9.2 측정 모드이고 같은 컴퓨터면 외부 AI 없이 처리, 가린 문장, 항목별 위치, 지연을 돌려준다")
    void measurementLocal() throws Exception {
        MockMvc m = mvc(true);

        MockHttpServletResponse r = chat(m, json("김민수 010-2345-6789", false), null);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).contains("\"action\":\"MASK\"", "\"maskedPrompt\":\"김민수 [전화1]\"",
                "\"item\":\"PHONE\"", "\"start\":4", "\"end\":17", "\"detectMs\":");
        assertThat(sentToAi).isEmpty();
    }

    @Test
    @DisplayName("AC-9.2 측정 모드에서는 차단도 200으로 같은 형식이다(측정 도구가 한 방식으로 읽는다)")
    void measurementBlockIs200() throws Exception {
        MockHttpServletResponse r = chat(mvc(true), json("password=Qw7!zLp92x", false), null);

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r)).contains("\"action\":\"BLOCK\"", "\"maskedPrompt\":");
    }

    @Test
    @DisplayName("AC-9.3 측정 모드라도 다른 컴퓨터의 요청은 일반 응답이고, 헤더로 127.0.0.1을 주장해도 마찬가지이다")
    void measurementRemote() throws Exception {
        MockMvc m = mvc(true);

        MockHttpServletResponse remote = chat(m, json("010-2345-6789", false), "203.0.113.5");
        assertThat(body(remote)).doesNotContain("maskedPrompt").contains("\"action\":\"MASK\"");

        MockHttpServletResponse spoofed = m.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(json("010-2345-6789", false)).header("X-Forwarded-For", "127.0.0.1")
                        .with(r -> {
                            r.setRemoteAddr("203.0.113.5");
                            return r;
                        }))
                .andReturn().getResponse();
        assertThat(body(spoofed)).doesNotContain("maskedPrompt");
    }
}
