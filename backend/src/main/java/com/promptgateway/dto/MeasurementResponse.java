package com.promptgateway.dto;

import com.promptgateway.detect.FindingCount;
import com.promptgateway.gateway.GatewayResult;

import java.util.List;

/**
 * 측정 모드 응답(명세 v2 AC-9.2). 같은 컴퓨터의 요청에만 준다. 측정 도구가 정답(넣은 값의 항목과 위치)과 비교하도록
 * 항목별 위치와 가린 문장을 싣는다. 탐지한 값 자체는 싣지 않는다(위치로 충분하다).
 */
public record MeasurementResponse(String action, boolean confirmed, List<FindingCount> findings, List<Span> spans,
                                  String maskedPrompt, GatewayResult.Timings timings) {

    public record Span(String category, String item, int start, int end) {
    }

    public static MeasurementResponse from(GatewayResult r) {
        List<Span> spans = r.findings() == null ? List.of() : r.findings().stream()
                .map(f -> new Span(f.category().name(), f.item().name(), f.start(), f.end())).toList();
        return new MeasurementResponse(r.action().name(), r.confirmed(), r.counts(), spans, r.maskedPrompt(), r.timings());
    }
}
