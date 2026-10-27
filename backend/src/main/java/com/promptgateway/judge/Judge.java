package com.promptgateway.judge;

/**
 * "프롬프트가 위반일 확률을 질문별로 돌려주는" 판정자. 지금 구현은 Jev(JevJudge) 하나뿐이지만,
 * 인터페이스로 분리해 두어서 다른 판정 모델로 바꿔도 ChatGateway 쪽 코드는 바뀌지 않는다.
 * 호출이 실패하면 JudgeException을 던진다.
 */
public interface Judge {

    JudgeResult judge(String prompt);
}
