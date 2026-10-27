package com.promptgateway.service;

import com.promptgateway.dto.gemini.GeminiRequest;
import com.promptgateway.dto.gemini.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

/**
 * "실제 LLM(Gemini)을 호출하는 방법을 아는" 유일한 클래스.
 * 컨트롤러는 이 서비스가 어떻게 Gemini를 부르는지 몰라도 되고, 그냥 generateAnswer(prompt)만 호출하면 된다.
 * -> 이게 컨트롤러/서비스를 나누는 이유: "요청을 받는 역할"과 "실제 작업을 하는 역할"을 분리해서,
 *    나중에 4단계에서 필터 로직이 추가돼도 컨트롤러 코드는 거의 안 바뀌게 된다.
 */
@Service
public class GeminiService implements AnswerGenerator {

    private final RestTemplate restTemplate;

    // application.yml의 값을 그대로 필드에 주입받는다. (0-1단계에서 찍먹했던 @Value가 여기서 실전으로 쓰임)
    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.base-url}")
    private String baseUrl;

    public GeminiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String generateAnswer(String prompt) {
        String url = baseUrl + "/" + model + ":generateContent?key=" + apiKey;

        GeminiRequest requestBody = GeminiRequest.of(prompt);

        // Content-Type에 charset=UTF-8을 명시한다.
        // (1단계 PowerShell에서 한글이 깨졌던 원인이 "전송 시 인코딩"이었다 - 자바의 Jackson은
        //  HTTP body를 쓸 때 기본적으로 UTF-8 바이트로 직렬화하기 때문에 원래도 안전하지만,
        //  헤더에도 명시적으로 UTF-8을 못박아서 서버가 다른 인코딩으로 오해할 여지를 아예 없앤다)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));

        HttpEntity<GeminiRequest> entity = new HttpEntity<>(requestBody, headers);

        // postForObject: POST 요청을 보내고, 응답 JSON을 바로 GeminiResponse 객체로 변환해서 받는다.
        // (1단계 PowerShell의 Invoke-RestMethod가 응답을 자동으로 파워쉘 객체로 바꿔줬던 것과 같은 역할)
        GeminiResponse response = restTemplate.postForObject(url, entity, GeminiResponse.class);

        if (response == null) {
            throw new IllegalStateException("Gemini API 응답이 비어 있습니다.");
        }
        return response.extractAnswerText();
    }
}
