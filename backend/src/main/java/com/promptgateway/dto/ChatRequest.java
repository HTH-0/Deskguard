package com.promptgateway.dto;

/**
 * /api/chat 요청 본문. confirmed는 경고를 본 직원이 "가려서 보내기"를 골랐다는 표시이다(AC-6.2). 없으면 false이다.
 */
public record ChatRequest(String prompt, Boolean confirmed) {

    public boolean isConfirmed() {
        return Boolean.TRUE.equals(confirmed);
    }
}
