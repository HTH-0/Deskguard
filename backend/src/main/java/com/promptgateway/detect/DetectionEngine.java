package com.promptgateway.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 모든 탐지기를 돌리고, 허용 목록의 값을 빼고, 겹친 탐지를 정리한다(docs/design.md 2장).
 * 겹치면 더 긴 탐지를 남기고, 길이가 같으면 범주 D2 > D3 > D1, 같은 범주 안에서는 Item 순서를 따른다.
 * 결과는 위치 순서이다.
 */
public class DetectionEngine {

    private static final Comparator<Finding> PRIORITY = Comparator
            .comparingInt(Finding::length).reversed()
            .thenComparingInt(f -> switch (f.category()) {
                case D2 -> 0;
                case D3 -> 1;
                case D1 -> 2;
            })
            .thenComparingInt(f -> f.item().ordinal());

    private final List<Detector> detectors;
    private final Set<String> allowlist;

    public DetectionEngine(List<Detector> detectors, Set<String> allowlist) {
        this.detectors = List.copyOf(detectors);
        this.allowlist = allowlist.stream().map(DetectionEngine::key).collect(Collectors.toUnmodifiableSet());
    }

    public List<Finding> detect(String text) {
        List<Finding> all = new ArrayList<>();
        for (Detector d : detectors) {
            for (Finding f : d.detect(text)) {
                if (!allowlist.contains(key(f.value()))) {
                    all.add(f);
                }
            }
        }
        all.sort(PRIORITY);
        List<Finding> kept = new ArrayList<>();
        for (Finding f : all) {
            if (kept.stream().noneMatch(f::overlaps)) {
                kept.add(f);
            }
        }
        kept.sort(Comparator.comparingInt(Finding::start));
        return kept;
    }

    /** 허용 목록 비교용: 대소문자, 공백, 하이픈, 점 차이를 무시한다. */
    static String key(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s.-]", "");
    }
}
