package com.promptgateway.filter;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 정규식으로 요청을 먼저 걸러내는 1차 단계(FR-2). 차단 전용이며 "통과 규칙"은 없다.
 * 패턴 목록이 비어 있으면 모든 입력이 통과한다(현재 패턴은 0개).
 * 잘못된 정규식은 서버가 시작될 때 알 수 있도록 생성할 때 바로 실패시킨다.
 */
public class PatternFilter {

    private record Compiled(String id, Pattern pattern) {
    }

    private final List<Compiled> patterns;

    public PatternFilter(List<PatternRule> rules) {
        this.patterns = rules.stream().map(PatternFilter::compile).toList();
    }

    private static Compiled compile(PatternRule rule) {
        try {
            return new Compiled(rule.id(), Pattern.compile(rule.regex()));
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("패턴 " + rule.id() + "의 정규식이 올바르지 않습니다: " + e.getDescription(), e);
        }
    }

    /** 먼저 일치한 패턴의 ID를 돌려준다. 일치하는 패턴이 없으면 빈 값이다. */
    public Optional<String> firstMatch(String prompt) {
        for (Compiled c : patterns) {
            if (c.pattern().matcher(prompt).find()) {
                return Optional.of(c.id());
            }
        }
        return Optional.empty();
    }
}
