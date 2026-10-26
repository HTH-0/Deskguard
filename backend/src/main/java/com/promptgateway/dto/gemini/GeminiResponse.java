package com.promptgateway.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Gemini API가 돌려주는 응답 body 전체 모양.
 * 1단계 학습노트(API_응답_예시.txt)에서 확인한 구조를 그대로 옮긴 것:
 *   { "candidates": [...], "usageMetadata": {...}, "modelVersion": "...", ... }
 * modelVersion, responseId 등은 안 받아도 되므로 필드로 안 적었고,
 * @JsonIgnoreProperties(ignoreUnknown = true) 덕분에 그 필드들이 와도 에러 없이 무시된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {

    /**
     * 이 응답에서 우리가 진짜로 필요한 건 결국 이 한 줄뿐이다:
     * candidates[0].content.parts[0].text
     * (1단계 PowerShell에서 $response.candidates[0].content.parts[0].text 로 꺼냈던 것과 동일)
     */
    public String extractAnswerText() {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 candidates가 비어 있습니다.");
        }
        Content content = candidates.get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 답변 텍스트(parts)가 없습니다.");
        }
        return content.parts().get(0).text();
    }
}
