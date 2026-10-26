package com.promptgateway.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * candidates 배열 안의 원소 하나.
 * 실제 응답엔 finishReason, index 등 필드가 더 있지만, 우리는 content만 쓰면 되므로
 * 나머지는 매핑하지 않는다.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) 가 핵심이다: 이게 없으면 Jackson은 기본적으로
 * "이 클래스에 없는 필드가 JSON에 있으면 에러를 낸다". Gemini 응답엔 이 클래스에 안 적어둔 필드가
 * 많아서(finishReason, index, thoughtSignature 등), 이 어노테이션 없이는 파싱이 실패한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Candidate(Content content) {
}
