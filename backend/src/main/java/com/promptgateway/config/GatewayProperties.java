package com.promptgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 gateway 아래 설정. 입력 최대 길이(기본 4,000자, 명세 v2 S1)와 측정 모드(기본 꺼짐).
 * 측정 모드는 켜도 같은 컴퓨터에서 온 요청에만 적용된다(ChatController).
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @DefaultValue("4000") int maxPromptLength,
        @DefaultValue("false") boolean measurementMode) {

    public GatewayProperties {
        if (maxPromptLength <= 0) {
            throw new IllegalArgumentException("gateway.max-prompt-length는 0보다 커야 합니다: " + maxPromptLength);
        }
    }
}
