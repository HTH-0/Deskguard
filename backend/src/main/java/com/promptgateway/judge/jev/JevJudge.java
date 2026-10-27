package com.promptgateway.judge.jev;

import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.Question;
import com.promptgateway.judge.QuestionSet;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 판정 모델 Jev를 부르는 Judge 구현. "이 문장이 위반일 확률"을 질문 10개에 대해 한 번의 호출로 받아 온다.
 *
 * 요청 모양: {"model": 버전ID, "state": 사용자 프롬프트, "questions": {질문ID: {type, instructions, criteria}}}
 * 응답 모양: {"model": ..., "answers": {질문ID: {"noul": 확률}}}
 *
 * JSON은 문자열을 직접 이어 붙이지 않고 Map을 만들어 스프링이 변환하게 한다.
 * 프롬프트에 따옴표나 줄바꿈이 있어도 JSON이 깨지지 않게 하기 위해서이다.
 */
public class JevJudge implements Judge {

    private static final String PATH = "/v1/systemone";

    private final RestClient restClient;
    private final QuestionSet questions;
    private final String model;
    private final String apiKey;

    public JevJudge(RestClient restClient, QuestionSet questions, String model, String apiKey) {
        this.restClient = restClient;
        this.questions = questions;
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public JudgeResult judge(String prompt) {
        long start = System.nanoTime();

        JevResponse response = restClient.post()
                .uri(PATH)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequest(prompt))
                .retrieve()
                .body(JevResponse.class);

        long latencyMillis = (System.nanoTime() - start) / 1_000_000;
        return new JudgeResult(readScores(response), modelVersion(response), latencyMillis);
    }

    private Map<String, Object> buildRequest(String prompt) {
        Map<String, Object> questionMap = new LinkedHashMap<>();
        for (Question q : questions.questions()) {
            Map<String, Object> criteria = new LinkedHashMap<>();
            criteria.put("true", q.trueCriteria());
            criteria.put("false", q.falseCriteria());

            Map<String, Object> question = new LinkedHashMap<>();
            question.put("type", "noul");
            question.put("instructions", q.instruction());
            question.put("criteria", criteria);
            questionMap.put(q.id(), question);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("state", prompt);
        body.put("questions", questionMap);
        return body;
    }

    private Map<String, Double> readScores(JevResponse response) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String id : questions.ids()) {
            JevResponse.Answer answer = response.answers().get(id);
            scores.put(id, answer.noul());
        }
        return scores;
    }

    /** 응답에 model 값이 있으면 그것을, 없으면 요청에 쓴 버전을 기록한다. */
    private String modelVersion(JevResponse response) {
        String fromResponse = response.model();
        return (fromResponse == null || fromResponse.isBlank()) ? model : fromResponse;
    }
}
