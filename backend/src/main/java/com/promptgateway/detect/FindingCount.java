package com.promptgateway.detect;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 응답과 로그에 싣는 탐지 요약: 범주, 항목, 개수. 값과 위치는 싣지 않는다(AC-6.3, 7.2). 순서는 Item 순서이다. */
public record FindingCount(Category category, Item item, int count) {

    public static List<FindingCount> of(List<Finding> findings) {
        Map<Item, Integer> counts = new EnumMap<>(Item.class);
        findings.forEach(f -> counts.merge(f.item(), 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted((a, b) -> a.getKey().category() != b.getKey().category()
                        ? a.getKey().category().compareTo(b.getKey().category())
                        : a.getKey().compareTo(b.getKey()))
                .map(e -> new FindingCount(e.getKey().category(), e.getKey(), e.getValue()))
                .toList();
    }
}
