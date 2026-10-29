package com.promptgateway;

import com.promptgateway.config.GatewayProperties;
import com.promptgateway.config.JudgeProperties;
import com.promptgateway.controller.ChatController;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.judge.Judge;
import com.promptgateway.judge.jev.JevJudge;
import com.promptgateway.service.AnswerGenerator;
import com.promptgateway.service.GeminiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 application.yml과 모든 부품을 연결해 서버가 시작되는지 확인한다. 키는 가짜 값이고 외부 호출은 없다.
 * 환경변수(GEMINI_API_KEY, TYPESAFE_API_KEY)가 없어도 통과한다.
 */
@SpringBootTest(properties = {"gemini.api-key=TEST-GEMINI-KEY", "judge.api-key=TEST-JEV-KEY"})
class PromptGatewayApplicationTest {

    @Autowired ChatController controller;
    @Autowired ChatGateway gateway;
    @Autowired Judge judge;
    @Autowired AnswerGenerator answerGenerator;
    @Autowired JudgeProperties judgeProperties;
    @Autowired GatewayProperties gatewayProperties;

    @Test
    @DisplayName("서버가 시작되고 부품이 설계대로 연결된다 (판정은 Jev, 응답은 Gemini)")
    void contextLoadsWithDesignedWiring() {
        assertThat(controller).isNotNull();
        assertThat(gateway).isNotNull();
        assertThat(judge).isInstanceOf(JevJudge.class);
        assertThat(answerGenerator).isInstanceOf(GeminiService.class);
    }

    @Test
    @DisplayName("application.yml의 설정이 설계서 값과 같다 (임계값 0.5, 모델 jev-1.14.0, 1,000자, 패턴 0개)")
    void applicationYmlMatchesDesign() {
        assertThat(judgeProperties.threshold()).isEqualTo(0.5);
        assertThat(judgeProperties.model()).isEqualTo("jev-1.14.0");
        assertThat(judgeProperties.baseUrl()).isEqualTo("https://api.typesafe.ai");
        assertThat(gatewayProperties.maxPromptLength()).isEqualTo(1000);
        assertThat(gatewayProperties.patterns()).isEmpty();
    }

    @Autowired org.springframework.core.env.Environment environment;

    @Test
    @DisplayName("AC-9.8 프록시 헤더(X-Forwarded-For 등)를 믿지 않도록 고정되어 있다 (클라우드 환경 감지로 켜지지 않게)")
    void forwardedHeadersAreNotTrusted() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualToIgnoringCase("none");
    }
}
