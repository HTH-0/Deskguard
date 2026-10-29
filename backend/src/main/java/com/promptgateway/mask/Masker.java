package com.promptgateway.mask;

import com.promptgateway.detect.Finding;
import com.promptgateway.detect.Item;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 탐지한 값을 가명으로 바꾸고, 외부 AI의 응답에서 가명을 원래 값으로 되돌린다(docs/design.md 4장, AC-4.1~4.5).
 *
 *   가명 형식      [항목한글명+순번], 예: [전화1]. 외부 AI가 무엇이었는지 짐작해 답할 수 있게 항목을 드러낸다
 *   같은 값        공백, 하이픈, 점, 대소문자를 무시하고 같으면 같은 가명이다
 *   이미 있는 글자  원문에 [전화1] 같은 글자가 있으면 그 순번을 건너뛴다
 *   복원          외부 AI가 가명을 조금 바꿔 써도([전화 1], ［전화1］) 찾는다. 대응표에 없는 가명 모양은 그대로 둔다
 *
 * 대응표는 MaskResult에만 있고 요청이 끝나면 버린다. 이 클래스는 상태를 갖지 않는다.
 */
public class Masker {

    private static final Pattern PSEUDONYM = Pattern.compile("[\\[［]\\s*([가-힣A-Za-z]+)\\s*(\\d+)\\s*[\\]］]");

    public MaskResult mask(String text, List<Finding> findings) {
        Map<String, String> pseudonymToValue = new LinkedHashMap<>();
        if (findings.isEmpty()) {
            return new MaskResult(text, pseudonymToValue);
        }
        Map<String, String> existing = existingPseudonyms(text);
        Map<String, String> valueKeyToPseudonym = new HashMap<>();
        Map<Item, Integer> nextNumber = new HashMap<>();

        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (Finding f : findings.stream().sorted((a, b) -> Integer.compare(a.start(), b.start())).toList()) {
            String valueKey = f.item().name() + ":" + normalize(f.value());
            String pseudonym = valueKeyToPseudonym.get(valueKey);
            if (pseudonym == null) {
                int n = nextNumber.getOrDefault(f.item(), 1);
                while (existing.containsKey(f.item().label() + n)) {
                    n++;
                }
                pseudonym = "[" + f.item().label() + n + "]";
                nextNumber.put(f.item(), n + 1);
                valueKeyToPseudonym.put(valueKey, pseudonym);
                pseudonymToValue.put(pseudonym, f.value());
            }
            out.append(text, cursor, f.start()).append(pseudonym);
            cursor = f.end();
        }
        out.append(text.substring(cursor));
        return new MaskResult(out.toString(), pseudonymToValue);
    }

    public RestoreResult restore(String answer, MaskResult mask) {
        if (answer == null || mask.pseudonymToValue().isEmpty()) {
            return new RestoreResult(answer, 0);
        }
        Map<String, String> byKey = new HashMap<>();
        mask.pseudonymToValue().forEach((pseudonym, value) -> {
            Matcher m = PSEUDONYM.matcher(pseudonym);
            if (m.matches()) {
                byKey.put(m.group(1) + m.group(2), value);
            }
        });
        Matcher m = PSEUDONYM.matcher(answer);
        StringBuilder out = new StringBuilder();
        int restored = 0;
        while (m.find()) {
            String value = byKey.get(m.group(1) + m.group(2));
            if (value != null) {
                m.appendReplacement(out, Matcher.quoteReplacement(value));
                restored++;
            } else {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(out);
        return new RestoreResult(out.toString(), restored);
    }

    private static Map<String, String> existingPseudonyms(String text) {
        Map<String, String> found = new HashMap<>();
        Matcher m = PSEUDONYM.matcher(text);
        while (m.find()) {
            found.put(m.group(1) + m.group(2), m.group());
        }
        return found;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s.-]", "");
    }
}
