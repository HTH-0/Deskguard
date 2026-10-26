package com.promptgateway.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate은 스프링이 기본으로 자동 등록해주지 않기 때문에,
 * 직접 @Bean으로 하나 만들어서 스프링 컨테이너에 등록해야 GeminiService에 주입할 수 있다.
 *
 * 여기서 타임아웃을 설정하는 이유: 3단계 로드맵의 "주의 - LLM API 호출 실패(타임아웃 등) 처리"에
 * 해당하는 부분이다. 이 설정이 없으면 Gemini 서버가 응답을 안 줄 때 우리 서버가 무한정 기다리게 된다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))  // 연결 자체가 안 될 때 10초까지만 기다림
                .readTimeout(Duration.ofSeconds(30))     // 연결은 됐는데 응답이 안 올 때 30초까지만 기다림
                .build();
    }
}
