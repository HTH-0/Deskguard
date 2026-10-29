package com.promptgateway.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D2 자격증명 탐지(docs/design.md 2장). 알려진 형식과 할당 표시로 찾고, 자리 표시자(xxxx, ****, your_, &lt;...&gt;)와
 * 흔한 예시 값(password, changeme 등)은 뺀다. 문서나 질문에 나오는 예시를 차단하지 않기 위해서이다.
 */
public class SecretDetector implements Detector {

    private record Rule(Item item, Pattern pattern, int valueGroup) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Item.PRIVATE_KEY, Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?(?:-----END [A-Z ]*PRIVATE KEY-----|$)"), 0),
            new Rule(Item.CONN_STRING, Pattern.compile("\\b[a-z][a-z0-9+.-]*://([^:@\\s/]+):([^@\\s/]+)@[^\\s]+"), 2),
            new Rule(Item.API_KEY, Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"), 0),
            new Rule(Item.API_KEY, Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"), 0),
            new Rule(Item.API_KEY, Pattern.compile("\\bsk-[A-Za-z0-9_-]{20,}"), 0),
            new Rule(Item.API_KEY, Pattern.compile("\\bghp_[A-Za-z0-9]{36}\\b"), 0),
            new Rule(Item.API_KEY, Pattern.compile("\\bxox[abprs]-[A-Za-z0-9-]{10,}"), 0),
            new Rule(Item.JWT, Pattern.compile("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"), 0),
            new Rule(Item.PASSWORD, Pattern.compile("(?i)(?:password|passwd|pwd|비밀번호|비번)\\w*\\s*[:=]\\s*[\"']?([^\\s\"',;]{4,})"), 1));

    /** 값에 들어 있으면 자리 표시자로 보는 조각. */
    private static final List<String> PLACEHOLDER_PARTS = List.of("your", "example", "xxxx", "****", "<", ">", "${", "{{");
    /** 값 전체가 이것과 같을 때만 예시로 보는 단어. 포함 여부로 보면 Password1! 같은 실제 약한 비밀번호를 놓친다. */
    private static final Set<String> PLACEHOLDER_VALUES = Set.of("password", "changeme", "secret", "dummy", "placeholder", "test");

    @Override
    public List<Finding> detect(String text) {
        List<Finding> found = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                String value = m.group(rule.valueGroup());
                if (rule.item() != Item.PRIVATE_KEY && isPlaceholder(value, rule.item())) {
                    continue;
                }
                int start = rule.item() == Item.PASSWORD ? m.start(1) : m.start();
                int end = rule.item() == Item.PASSWORD ? m.end(1) : m.end();
                found.add(new Finding(rule.item(), start, end, text.substring(start, end)));
            }
        }
        return found;
    }

    private static boolean isPlaceholder(String value, Item item) {
        String v = value.toLowerCase(Locale.ROOT);
        String body = switch (item) {
            case API_KEY -> v.replaceFirst("^(akia|aiza|sk-(proj-)?|ghp_|xox[abprs]-)", "");
            default -> v;
        };
        if (PLACEHOLDER_VALUES.contains(body) || PLACEHOLDER_PARTS.stream().anyMatch(body::contains)) {
            return true;
        }
        long distinct = body.chars().filter(Character::isLetterOrDigit).distinct().count();
        return distinct < 3;
    }
}
