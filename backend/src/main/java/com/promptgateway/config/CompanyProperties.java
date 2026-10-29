package com.promptgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 회사 사전(src/main/resources/company/dictionary.yml, docs/dummy-company.md). 코드명, 고객사, 등급 표시는 비어 있으면 안 된다.
 * 빈 단어도 허용하지 않는다(DictionaryDetector). 잘못되면 서버가 시작되지 않는다(AC-2.7).
 */
@ConfigurationProperties(prefix = "company")
public record CompanyProperties(List<String> codenames, List<String> clients, List<String> classified, List<String> allowlist) {

    public CompanyProperties {
        requireNotEmpty("company.codenames", codenames);
        requireNotEmpty("company.clients", clients);
        requireNotEmpty("company.classified", classified);
        allowlist = allowlist == null ? List.of() : List.copyOf(allowlist);
    }

    private static void requireNotEmpty(String name, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + "가 비어 있습니다. 회사 사전을 확인하세요.");
        }
    }
}
