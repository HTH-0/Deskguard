package com.promptgateway.log;

import com.promptgateway.gateway.GatewayResult;
import com.promptgateway.judge.JudgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 요청 하나가 끝날 때마다 "key=value" 형식의 로그를 한 줄 남긴다(docs/design.md 7.2절).
 * 이 한 줄이 6단계 측정과 지연 분석의 원천 데이터이다.
 *
 * 남기지 않는 것: API 키, 예외 메시지(주소나 키가 들어 있을 수 있다), 판정 요청 본문 전체.
 * 프롬프트 원문은 원문 저장을 켠 경우에만 남기고, 기본으로는 길이와 해시만 남긴다.
 * 해시는 같은 입력이 반복되는지 찾는 용도이다. 짧은 입력은 해시로 원문을 추측할 수 있어서 익명화는 아니다.
 *
 * 로그가 나가는 곳(sink)은 기본이 SLF4J이고, 시험에서는 목록으로 바꿔 끼운다.
 * SLF4J로 남길 때의 수준(AC-7.6): 통과와 정책 차단은 INFO, 판정 실패와 ERROR는 WARN, 판정 설정 오류는 ERROR(운영 경보).
 * 원문과 외부에서 온 값은 이스케이프해서 한 줄을 벗어나거나 가짜 항목을 만들 수 없게 한다(AC-7.7).
 */
public class GatewayLogger {

    private static final Logger log = LoggerFactory.getLogger(GatewayLogger.class);

    private static final int HASH_HEX_LENGTH = 16;

    /** 단계별 지연 시간(밀리초). 실행하지 않은 단계는 null이고, 로그에서 빠진다. */
    public record Timings(Long filterMs, Long judgeMs, Long answerMs) {
        public static final Timings NONE = new Timings(null, null, null);
    }

    private final boolean storeRawPrompt;
    private final Consumer<String> sink;

    /** SLF4J로 남긴다. 수준은 결과의 심각도를 따른다. */
    public GatewayLogger(boolean storeRawPrompt) {
        this(storeRawPrompt, null);
    }

    /** 시험용: 줄을 sink로 보낸다(수준은 구분하지 않는다). */
    public GatewayLogger(boolean storeRawPrompt, Consumer<String> sink) {
        this.storeRawPrompt = storeRawPrompt;
        this.sink = sink;
    }

    public boolean storesRawPrompt() {
        return storeRawPrompt;
    }

    /** 게이트웨이가 통과 또는 차단으로 끝낸 요청을 기록한다. */
    public void logResult(String prompt, GatewayResult result, Timings timings) {
        Map<String, String> fields = baseFields();
        fields.put("decision", result.decision() == GatewayResult.Decision.PASSED ? "PASS" : "BLOCK");
        fields.put("stage", result.stage().name());
        putIfPresent(fields, "patternId", result.patternId());
        if (result.failureKind() != null) {
            fields.put("failureKind", result.failureKind().name());
        }
        if (result.judgeResult() != null) {
            fields.put("modelVersion", value(result.judgeResult().modelVersion()));
            fields.put("scores", formatScores(result.judgeResult().scores()));
        }
        putTimings(fields, timings);
        putPrompt(fields, prompt);
        Level level = result.failureKind() == null ? Level.INFO
                : result.failureKind() == JudgeException.Kind.CONFIG_ERROR ? Level.ERROR : Level.WARN;
        emit(level, fields);
    }

    /** 입력 검증 실패나 예상하지 못한 오류처럼 결과 없이 예외로 끝난 요청을 기록한다. 예외 메시지는 싣지 않는다. */
    public void logError(String prompt, String stage, Throwable cause, Timings timings) {
        Map<String, String> fields = baseFields();
        fields.put("decision", "ERROR");
        fields.put("stage", stage);
        fields.put("errorKind", cause.getClass().getSimpleName());
        putTimings(fields, timings);
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

    private static Map<String, String> baseFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", UUID.randomUUID().toString());
        return fields;
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static void putTimings(Map<String, String> fields, Timings timings) {
        putIfPresent(fields, "filterMs", timings.filterMs() == null ? null : timings.filterMs().toString());
        putIfPresent(fields, "judgeMs", timings.judgeMs() == null ? null : timings.judgeMs().toString());
        putIfPresent(fields, "answerMs", timings.answerMs() == null ? null : timings.answerMs().toString());
    }

    private void putPrompt(Map<String, String> fields, String prompt) {
        // 입력 검증과 같은 기준(코드 포인트)으로 센다. 이모지는 1자이다.
        fields.put("promptLength", String.valueOf(prompt == null ? 0 : prompt.codePointCount(0, prompt.length())));
        if (prompt == null) {
            return;
        }
        fields.put("promptHash", hash(prompt));
        if (storeRawPrompt) {
            fields.put("prompt", "\"" + escape(prompt) + "\"");
        }
    }

    /** 질문 ID 순서로 "ID:확률"을 쉼표로 잇는다. */
    private static String formatScores(Map<String, Double> scores) {
        return new TreeMap<>(scores).entrySet().stream()
                .map(e -> e.getKey() + ":" + String.format(Locale.ROOT, "%.3f", e.getValue()))
                .collect(Collectors.joining(","));
    }

    /** 외부에서 온 값. 공백, 따옴표, 등호, 쉼표, 제어 문자가 있으면 따옴표로 감싸고 이스케이프한다. */
    private static String value(String v) {
        if (v == null || v.isEmpty()) {
            return v;
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c <= ' ' || c == '"' || c == '=' || c == '\\' || c == ',' || needsUnicodeEscape(c)) {
                return "\"" + escape(v) + "\"";
            }
        }
        return v;
    }

    /** 원문이 한 줄을 벗어나지 않도록 역슬래시, 따옴표, 줄바꿈, 탭, 제어 문자를 이스케이프한다. */
    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (needsUnicodeEscape(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 제어 문자와 줄 구분 문자(U+2028, U+2029). 일부 로그 도구는 이 문자에서 줄을 나눈다. */
    private static boolean needsUnicodeEscape(char c) {
        return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
    }

    private void emit(Level level, Map<String, String> fields) {
        String line = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(" "));
        if (sink != null) {
            sink.accept(line);
        } else {
            log.atLevel(level).log(line);
        }
    }
}
