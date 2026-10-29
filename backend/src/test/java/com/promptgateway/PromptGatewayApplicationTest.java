package com.promptgateway;

import com.promptgateway.config.GatewayProperties;
import com.promptgateway.config.CompanyProperties;
import com.promptgateway.config.DetectionProperties;
import com.promptgateway.controller.ChatController;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.GeminiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 application.yml과 모든 부품을 연결해 서버가 시작되는지 확인한다. 키는 가짜 값이고 외부 호출은 없다.
 * 환경변수(GEMINI_API_KEY)가 없어도 통과한다. 운영 경로에는 판정 서비스(Jev)가 없으므로 그 키는 필요 없다.
 */
@SpringBootTest(properties = {"gemini.api-key=TEST-GEMINI-KEY"})
class PromptGatewayApplicationTest {

    @Autowired ChatController controller;
    @Autowired ChatGateway gateway;
    @Autowired AnswerGenerator answerGenerator;
    @Autowired CompanyProperties companyProperties;
    @Autowired DetectionProperties detectionProperties;
    @Autowired GatewayProperties gatewayProperties;

    @Test
    @DisplayName("서버가 시작되고 부품이 설계대로 연결된다 (응답은 Gemini, 판정 서비스 없음)")
    void contextLoadsWithDesignedWiring() {
        assertThat(controller).isNotNull();
        assertThat(gateway).isNotNull();
        assertThat(answerGenerator).isInstanceOf(GeminiService.class);
    }

    @Test
    @DisplayName("application.yml과 회사 사전이 설계 값으로 읽힌다 (4,000자, 대량 기준 20, 가상 회사 HTH 사전)")
    void applicationYmlMatchesDesign() {
        assertThat(gatewayProperties.measurementMode()).isFalse();
        assertThat(gatewayProperties.maxPromptLength()).isEqualTo(4000);
        assertThat(detectionProperties.bulkThreshold()).isEqualTo(20);
        assertThat(companyProperties.codenames()).contains("블루웨일", "NIMBUS-7");
        assertThat(companyProperties.allowlist()).contains("contact@hth.example");
    }

    @Autowired org.springframework.core.env.Environment environment;

    @Test
    @DisplayName("AC-9.8 프록시 헤더(X-Forwarded-For 등)를 믿지 않도록 고정되어 있다 (클라우드 환경 감지로 켜지지 않게)")
    void forwardedHeadersAreNotTrusted() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualToIgnoringCase("none");
    }
}
