package com.promptgateway.log;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.gateway.GatewayResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 감사 로그: 요청 하나에 key=value 한 줄(명세 v2 FR-7, policy.md 5장).
 *
 *   남기는 것    요청 ID, 처리, 범주별 개수(findings=D1.PHONE:2,...), 되돌린 가명 수, 원문의 길이와 해시, 단계별 지연
 *   남기지 않는 것 원문, 탐지한 값, 가린 문장, 가명 대응표, 예외 메시지(주소나 키가 들어 있을 수 있다)
 *
 * 해시는 같은 입력이 반복되는지 찾는 용도이다. 짧은 입력은 해시로 추측할 수 있어서 익명화는 아니다.
 * SLF4J 수준: 허용과 가림은 INFO, 경고와 차단과 오류는 WARN(보안 이벤트). 로그가 나가는 곳은 시험에서 목록으로 바꿔 끼운다.
 */
public class GatewayLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogger.class);
    private static final int HASH_HEX_LENGTH = 16;

    private final Consumer<String> sink;

    public GatewayLogger() {
        this(null);
    }

    /** 시험용: 줄을 sink로 보낸다(수준은 구분하지 않는다). */
    public GatewayLogger(Consumer<String> sink) {
        this.sink = sink;
    }

    public void logResult(String prompt, GatewayResult result) {
        Map<String, String> fields = base();
        fields.put("action", result.action().name());
        if (result.confirmed()) {
            fields.put("confirmed", "true");
        }
        if (result.measured()) {
            fields.put("measure", "true");
        }
        fields.put("findings", result.counts().isEmpty() ? "-" : result.counts().stream()
                .map(c -> c.category() + "." + c.item() + ":" + c.count()).collect(Collectors.joining(",")));
        fields.put("restored", String.valueOf(result.restored()));
        GatewayResult.Timings t = result.timings();
        fields.put("detectMs", String.valueOf(t.detectMs()));
        putIfPresent(fields, "maskMs", t.maskMs());
        putIfPresent(fields, "answerMs", t.answerMs());
        putIfPresent(fields, "restoreMs", t.restoreMs());
        putPrompt(fields, prompt);
        Action a = result.action();
        emit(a == Action.BLOCK || a == Action.WARN ? Level.WARN : Level.INFO, fields);
    }

    /** 예외로 끝난 요청(입력 검증 실패, 외부 AI 오류). 예외의 종류만 남기고 메시지는 남기지 않는다. */
    public void logError(String prompt, String stage, Throwable cause) {
        Map<String, String> fields = base();
        fields.put("action", "ERROR");
        fields.put("stage", stage);
        fields.put("errorKind", cause.getClass().getSimpleName());
        putPrompt(fields, prompt);
        emit(Level.WARN, fields);
    }

    /** 프롬프트의 SHA-256 앞 16자리(16진수). */
    public static String hash(String prompt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }

    private static Map<String, String> base() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", UUID.randomUUID().toString());
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, Long value) {
        if (value != null) {
            fields.put(key, value.toString());
        }
    }

    private static void putPrompt(Map<String, String> fields, String prompt) {
        if (prompt == null) {
            return;
        }
        // 입력 검증과 같은 기준(코드 포인트)으로 센다
        fields.put("promptLength", String.valueOf(prompt.codePointCount(0, prompt.length())));
        fields.put("promptHash", hash(prompt));
    }

    private void emit(Level level, Map<String, String> fields) {
        String line = fields.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" "));
        if (sink != null) {
            sink.accept(line);
        } else {
            log.atLevel(level).log(line);
        }
    }
}
