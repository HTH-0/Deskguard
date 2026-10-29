package com.promptgateway.config;

import com.promptgateway.detect.Action;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.service.AnswerGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 설정 연결과 시작 검증(AC-2.7). 실제 서버를 띄우지 않는다. */
class GatewayConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfig.class)
            .withBean(AnswerGenerator.class, () -> prompt -> "답")
            .withPropertyValues("company.codenames=블루웨일", "company.clients=한결물산", "company.classified=대외비",
                    "company.allowlist=contact@hth.example");

    @Test
    @DisplayName("기본값: 최대 4,000자, 대량 기준 20, 측정 모드 꺼짐. 사전으로 조립된 게이트웨이가 동작한다")
    void defaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GatewayProperties.class).maxPromptLength()).isEqualTo(4000);
            assertThat(context.getBean(GatewayProperties.class).measurementMode()).isFalse();
            assertThat(context.getBean(DetectionProperties.class).bulkThreshold()).isEqualTo(20);
            ChatGateway gateway = context.getBean(ChatGateway.class);
            assertThat(gateway.handle("블루웨일 일정 정리", false, false).action()).isEqualTo(Action.MASK);
            assertThat(gateway.handle("문의 contact@hth.example", false, false).action()).isEqualTo(Action.ALLOW);
        });
    }

    @Test
    @DisplayName("AC-2.7 회사 사전이 비어 있거나 빈 단어가 있으면 서버가 시작되지 않는다")
    void invalidDictionaryFails() {
        new ApplicationContextRunner().withUserConfiguration(GatewayConfig.class)
                .withBean(AnswerGenerator.class, () -> prompt -> "답")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("company.codenames=블루웨일,  ").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("대량 기준이 0 이하이면 시작되지 않는다")
    void invalidThresholdFails() {
        runner.withPropertyValues("detection.bulk-threshold=0").run(context -> assertThat(context).hasFailed());
    }
}
