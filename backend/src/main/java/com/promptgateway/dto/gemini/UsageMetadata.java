package com.promptgateway.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토큰 사용량 정보. 1단계 학습노트에서 본 promptTokenCount / candidatesTokenCount /
 * thoughtsTokenCount / totalTokenCount 등이 여기 담긴다.
 * 지금 당장은 로그로 찍어보는 용도 정도만 쓸 거라, 자주 볼 3개만 필드로 받아둔다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsageMetadata(int promptTokenCount, int candidatesTokenCount, int totalTokenCount) {
}
