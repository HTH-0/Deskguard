package com.promptgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * application.yml의 judge 아래 설정(판정 모델 Jev 호출에 쓰는 값). 기본값은 설계서 6장과 같다.
 * 값이 잘못되면 서버가 시작될 때 바로 실패한다(엉뚱한 설정으로 조용히 동작하지 않게 하기 위해서이다).
 */
@ConfigurationProperties(prefix = "judge")
public record JudgeProperties(
        String apiKey,
        @DefaultValue("https://api.typesafe.ai") String baseUrl,
        @DefaultValue("jev-1.14.0") String model,
        @DefaultValue("0.5") double threshold,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("200ms") Duration retryBackoff,
        @DefaultValue("judge/questions.tsv") String questionsFile) {

    public JudgeProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("judge.api-key가 설정되지 않았습니다. 환경변수 TYPESAFE_API_KEY를 확인하세요.");
        }
        if (model == null || model.isBlank() || model.toLowerCase().endsWith("latest")) {
            throw new IllegalArgumentException("judge.model은 별칭(" + model + ")이 아니라 버전 ID(예: jev-1.14.0)여야 합니다.");
        }
        if (threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("judge.threshold는 0보다 크고 1 이하여야 합니다: " + threshold);
        }
        if (connectTimeout.isZero() || connectTimeout.isNegative() || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("judge의 타임아웃은 0보다 커야 합니다.");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("judge.max-retries는 0 이상이어야 합니다: " + maxRetries);
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("judge.retry-backoff는 음수일 수 없습니다.");
        }
    }
}
