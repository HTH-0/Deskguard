package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 탐지기 조합, 허용 목록, 겹침 정리(AC-2.5, design.md 2장). */
class DetectionEngineTest {

    final DetectionEngine engine = new DetectionEngine(
            List.of(new PersonalDataDetector(), new SecretDetector(),
                    new DictionaryDetector(List.of("블루웨일"), List.of("한결물산"), List.of("대외비"))),
            Set.of("contact@hth.example"));

    @Test
    @DisplayName("여러 범주를 한 번에 찾고 위치 순서로 돌려준다")
    void combinesDetectors() {
        List<Finding> found = engine.detect("블루웨일 담당 010-2345-6789, password=Qw7!zLp92x");

        assertThat(found).extracting(Finding::item).containsExactly(Item.CODENAME, Item.PHONE, Item.PASSWORD);
    }

    @Test
    @DisplayName("AC-2.5 허용 목록의 값은 빼다")
    void allowlist() {
        assertThat(engine.detect("문의는 contact@hth.example, 담당 kim@mail.example")).extracting(Finding::value)
                .containsExactly("kim@mail.example");
    }

    @Test
    @DisplayName("겹친 탐지는 더 긴 것 하나만 남긴다(접속 문자열 안의 이메일 모양을 따로 세지 않는다)")
    void overlapsKeepLongest() {
        List<Finding> found = engine.detect("jdbc mysql://admin:Gx82!kqP@db.hth.example:3306/app");

        assertThat(found).extracting(Finding::item).containsExactly(Item.CONN_STRING);
    }

    @Test
    @DisplayName("AC-2.8 탐지는 외부 호출 없이 끝난다(탐지기는 순수 코드다)")
    void noExternalCalls() {
        assertThat(engine.detect("평범한 회의록 요약해 줘")).isEmpty();
    }
}
