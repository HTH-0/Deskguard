package com.promptgateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.gateway.GatewayResult;

import java.util.List;

/**
 * /api/chat 응답 본문(design.md 5장). findings에는 범주, 항목, 개수만 있고 값과 위치는 없다(AC-6.3).
 * confirmRequired와 message는 경고와 차단에서만, sentPrompt(외부 AI로 보낸 가린 문장)는 가림에서만 싣는다(AC-6.4).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(String action, String answer, List<FindingCount> findings, Boolean confirmRequired, String message,
                           String sentPrompt) {

    static final String BLOCK_MESSAGE = "자격증명이나 보안 등급 표시가 있는 내용은 외부 AI로 보내지 않습니다. 해당 값을 지우고 다시 보내세요.";

    public static ChatResponse from(GatewayResult r) {
        return switch (r.action()) {
            case ALLOW -> new ChatResponse("ALLOW", r.answer(), r.counts(), null, null, null);
            case MASK -> new ChatResponse("MASK", r.answer(), r.counts(), null, null, r.maskedPrompt());
            case WARN -> new ChatResponse("WARN", null, r.counts(), true,
                    "개인정보가 많이 들어 있습니다. 가려서 보낼지 확인해 주세요.", null);
            case BLOCK -> new ChatResponse("BLOCK", null, r.counts(), null, BLOCK_MESSAGE, null);
        };
    }
}
