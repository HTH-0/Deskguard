package com.promptgateway.config;

import com.promptgateway.detect.ActionDecider;
import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.DictionaryDetector;
import com.promptgateway.detect.PersonalDataDetector;
import com.promptgateway.detect.SecretDetector;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.InputValidator;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.mask.Masker;
import com.promptgateway.service.AnswerGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;

/**
 * 게이트웨이 부품을 설정 값으로 만들어 연결한다. 설정이 잘못되었으면(빈 사전, 0 이하 기준) 부품을 만드는 단계에서
 * 예외가 나므로 서버가 시작되지 않는다(AC-2.7). 운영 경로에는 외부 판정 서비스가 없다(NFR-4).
 */
@Configuration
@EnableConfigurationProperties({GatewayProperties.class, DetectionProperties.class, CompanyProperties.class})
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public InputValidator inputValidator(GatewayProperties gateway) {
        return new InputValidator(gateway.maxPromptLength());
    }

    @Bean
    public DetectionEngine detectionEngine(CompanyProperties company) {
        return new DetectionEngine(
                List.of(new PersonalDataDetector(), new SecretDetector(),
                        new DictionaryDetector(company.codenames(), company.clients(), company.classified())),
                new HashSet<>(company.allowlist()));
    }

    @Bean
    public ActionDecider actionDecider(DetectionProperties detection) {
        return new ActionDecider(detection.bulkThreshold());
    }

    @Bean
    public Masker masker() {
        return new Masker();
    }

    @Bean
    public GatewayLogger gatewayLogger() {
        return new GatewayLogger();
    }

    @Bean
    public ChatGateway chatGateway(InputValidator validator, DetectionEngine engine, ActionDecider decider, Masker masker,
                                   AnswerGenerator answerGenerator, GatewayLogger gatewayLogger, GatewayProperties gateway) {
        if (gateway.measurementMode()) {
            log.warn("측정 모드가 켜져 있습니다. 같은 컴퓨터에서 온 요청은 외부 AI를 부르지 않고 탐지 결과를 받습니다. 사용자에게 노출하는 환경에서는 꺼야 합니다.");
        }
        return new ChatGateway(validator, engine, decider, masker, answerGenerator, gatewayLogger, gateway.measurementMode());
    }
}
