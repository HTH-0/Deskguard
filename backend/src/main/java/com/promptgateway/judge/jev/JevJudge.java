package com.promptgateway.judge.jev;

import com.promptgateway.judge.Judge;
import com.promptgateway.judge.JudgeException;
import com.promptgateway.judge.JudgeException.Kind;
import com.promptgateway.judge.JudgeResult;
import com.promptgateway.judge.Question;
import com.promptgateway.judge.QuestionSet;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
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
 *
 * 호출이 실패하면 원인(Kind)을 붙인 JudgeException을 던진다. 429와 529(요청 과다)만 지수로 늘려 가며 다시 시도하고,
 * 나머지 실패는 시도하지 않는다(사용자 대기 시간이 길어지고 장애 때 요청이 쌓이는 것을 막기 위해서이다).
 * 예외 메시지에는 API 키와 Jev 응답 본문을 넣지 않는다.
 */
public class JevJudge implements Judge {

    /** 재시도 사이에 기다리는 방법. 시험에서는 실제로 기다리지 않는 구현으로 바꿔 끼운다. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static final String PATH = "/v1/systemone";
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(200);

    private final RestClient restClient;
    private final QuestionSet questions;
    private final String model;
    private final String apiKey;
    private final int maxRetries;
    private final Duration backoff;
    private final Sleeper sleeper;

    public JevJudge(RestClient restClient, QuestionSet questions, String model, String apiKey) {
        this(restClient, questions, model, apiKey, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF, Thread::sleep);
    }

    public JevJudge(RestClient restClient, QuestionSet questions, String model, String apiKey,
                    int maxRetries, Duration backoff, Sleeper sleeper) {
        this.restClient = restClient;
        this.questions = questions;
        this.model = model;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
        this.backoff = backoff;
        this.sleeper = sleeper;
    }

    @Override
    public JudgeResult judge(String prompt) {
        long start = System.nanoTime();

        JevResponse response = callWithRetry(buildRequest(prompt));

        long latencyMillis = (System.nanoTime() - start) / 1_000_000;
        return new JudgeResult(readScores(response), modelVersion(response), latencyMillis);
    }

    private JevResponse callWithRetry(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return restClient.post()
                        .uri(PATH)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(JevResponse.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRateLimit(status) && attempt < maxRetries) {
                    pause(backoff.toMillis() << attempt);   // 200ms, 400ms, ... 처럼 두 배씩 늘린다
                    continue;
                }
                throw failureFor(status, e.getResponseBodyAsString());
            } catch (ResourceAccessException e) {
                // 연결 실패와 읽기 시간 초과는 모두 "응답을 받지 못함"이므로 같은 종류로 다룬다.
                throw new JudgeException(Kind.TIMEOUT, "Jev 응답을 받지 못했습니다(연결 실패 또는 시간 초과).", e);
            } catch (RestClientException e) {
                // 응답은 왔지만 JSON으로 읽을 수 없는 경우(깨진 JSON, JSON이 아닌 형식)
                throw new JudgeException(Kind.MALFORMED_RESPONSE, "Jev 응답을 읽을 수 없습니다.");
            }
        }
    }

    private static boolean isRateLimit(int status) {
        return status == 429 || status == 529;
    }

    private static JudgeException failureFor(int status, String upstreamBody) {
        if (isRateLimit(status)) {
            return new JudgeException(Kind.RATE_LIMITED, "Jev가 " + status + "을(를) 반환했습니다(재시도 후에도 실패).");
        }
        if (status == 400 && upstreamBody != null && upstreamBody.contains("max_tokens_exceeded")) {
            return new JudgeException(Kind.INPUT_TOO_LONG, "Jev가 입력 길이 초과(400)를 반환했습니다.");
        }
        if (status >= 400 && status < 500) {
            // 401(인증), 422(요청 형식), 그 밖의 400번대는 우리 설정이나 요청 구성의 문제로 본다.
            return new JudgeException(Kind.CONFIG_ERROR, "Jev가 " + status + "을(를) 반환했습니다(설정 확인 필요).");
        }
        return new JudgeException(Kind.SERVER_ERROR, "Jev가 " + status + "을(를) 반환했습니다.");
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgeException(Kind.TIMEOUT, "재시도 대기 중에 중단되었습니다.", e);
        }
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

    /**
     * 응답에서 질문별 확률을 읽는다. 확률이 없거나 0~1 범위를 벗어나면 믿을 수 없는 응답으로 본다.
     * 질문 하나가 통째로 빠진 경우는 여기서 걸러내지 않고 그대로 둔다. 누락 판정은 BlockDecider의 몫이다.
     */
    private Map<String, Double> readScores(JevResponse response) {
        if (response == null || response.answers() == null || response.answers().isEmpty()) {
            throw new JudgeException(Kind.MALFORMED_RESPONSE, "Jev 응답에 answers가 없습니다.");
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String id : questions.ids()) {
            JevResponse.Answer answer = response.answers().get(id);
            if (answer == null) {
                continue;
            }
            Double value = answer.noul();
            if (value == null || value.isNaN() || value < 0.0 || value > 1.0) {
                throw new JudgeException(Kind.MALFORMED_RESPONSE, "질문 " + id + "의 확률이 올바르지 않습니다.");
            }
            scores.put(id, value);
        }
        return scores;
    }

    /** 응답에 model 값이 있으면 그것을, 없으면 요청에 쓴 버전을 기록한다. */
    private String modelVersion(JevResponse response) {
        String fromResponse = response.model();
        return (fromResponse == null || fromResponse.isBlank()) ? model : fromResponse;
    }
}
