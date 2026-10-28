package com.promptgateway.config;

import com.promptgateway.filter.PatternRule;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * application.yml의 gateway 아래 설정. 입력 최대 길이와 패턴 필터의 패턴 목록이다.
 * 패턴 목록의 기본값은 빈 목록이며, 비어 있으면 패턴 필터는 모든 요청을 통과시킨다.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @DefaultValue("1000") int maxPromptLength,
        @DefaultValue List<PatternRule> patterns,
        @DefaultValue("false") boolean measurementMode) {

    public GatewayProperties {
        if (maxPromptLength <= 0) {
            throw new IllegalArgumentException("gateway.max-prompt-length는 0보다 커야 합니다: " + maxPromptLength);
        }
    }
}
