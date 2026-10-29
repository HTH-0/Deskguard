package com.promptgateway.support;

import com.promptgateway.detect.ActionDecider;
import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.DictionaryDetector;
import com.promptgateway.detect.PersonalDataDetector;
import com.promptgateway.detect.SecretDetector;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.InputValidator;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.mask.Masker;
import com.promptgateway.service.AnswerGenerator;

import java.util.List;
import java.util.Set;

/** 시험용 게이트웨이 조립. 가상 회사 HTH의 사전 일부와 대량 기준 20을 쓴다. 외부 AI는 시험이 넘겨 준 가짜이다. */
public final class TestGateways {

    private TestGateways() {
    }

    public static DetectionEngine engine() {
        return new DetectionEngine(
                List.of(new PersonalDataDetector(), new SecretDetector(),
                        new DictionaryDetector(List.of("블루웨일", "NIMBUS-7"), List.of("한결물산"), List.of("대외비", "Confidential"))),
                Set.of("contact@hth.example", "02-555-0100"));
    }

    public static ChatGateway gateway(AnswerGenerator answerer, GatewayLogger logger, boolean measurementMode) {
        return new ChatGateway(new InputValidator(4000), engine(), new ActionDecider(20), new Masker(), answerer, logger,
                measurementMode);
    }

    /** 휴대전화 번호 n개가 든 명단 문장(대량 경고 시험용). */
    public static String phoneList(int n) {
        StringBuilder sb = new StringBuilder("아래 명단 표로 정리해 줘\n");
        for (int i = 0; i < n; i++) {
            sb.append("직원").append(i).append(" 010-").append(2000 + i).append("-").append(3000 + i).append('\n');
        }
        return sb.toString();
    }
}
