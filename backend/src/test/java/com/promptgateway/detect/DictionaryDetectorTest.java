package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D3 회사 사전 탐지(AC-2.4). 사전은 가상 회사의 값이다(docs/dummy-company.md). */
class DictionaryDetectorTest {

    final DictionaryDetector detector = new DictionaryDetector(
            List.of("블루웨일", "NIMBUS-7"), List.of("한결물산"), List.of("대외비", "Confidential"));

    @Test
    @DisplayName("AC-2.4 코드명, 고객사, 등급 표시를 대소문자 구분 없이 찾고 위치를 돌려준다")
    void findsDictionaryWords() {
        String text = "[대외비] 블루웨일 일정과 한결물산 계약, nimbus-7 / confidential";
        List<Finding> found = detector.detect(text);

        assertThat(found).extracting(Finding::item)
                .containsExactlyInAnyOrder(Item.CLASSIFIED, Item.CODENAME, Item.CLIENT, Item.CODENAME, Item.CLASSIFIED);
        Finding nimbus = found.stream().filter(f -> f.value().equalsIgnoreCase("NIMBUS-7")).findFirst().orElseThrow();
        assertThat(text.substring(nimbus.start(), nimbus.end())).isEqualTo("nimbus-7");
    }

    @Test
    @DisplayName("사전에 없는 단어는 찾지 않는다")
    void ignoresOthers() {
        assertThat(detector.detect("고래 사진 정리해 줘")).isEmpty();
    }
}
