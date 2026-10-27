package com.promptgateway.service;

/**
 * "통과한 프롬프트에 답변을 만들어 주는" 응답 모델. 지금 구현은 GeminiService 하나이다.
 * ChatGateway가 구체적인 Gemini 코드를 몰라도 되게 하고, 시험에서는 가짜 응답 모델로 바꿔 끼운다.
 */
public interface AnswerGenerator {

    String generateAnswer(String prompt);
}
