package com.promptgateway.judge.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Jev 응답 중 우리가 쓰는 부분만 받는다: model(응답한 모델 버전)과 answers(질문 ID별 noul 확률). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, Answer> answers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Answer(Double noul) {
    }
}
