package com.promptgateway.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 시연용 가짜 응답 모델(gateway.demo-answer=true). 외부 AI를 부르지 않고 받은 문장을 그대로 되돌려 준다.
 * 키 없이 포털을 시연하기 위한 것이다. 가림 처리된 요청이면 받은 문장에 가명이 들어 있고, 게이트웨이가 복원해서 직원에게 보여 준다.
 */
@Service
@ConditionalOnProperty(name = "gateway.demo-answer", havingValue = "true")
public class DemoAnswerGenerator implements AnswerGenerator {

    @Override
    public String generateAnswer(String prompt) {
        return "(시연용 답변: 외부 AI를 부르지 않았습니다) 받은 요청은 다음과 같습니다.\n" + prompt;
    }
}
