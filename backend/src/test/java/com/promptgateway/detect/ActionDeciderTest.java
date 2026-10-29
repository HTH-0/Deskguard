package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 처리 결정(AC-3.1~3.3, docs/design.md 3장). */
class ActionDeciderTest {

    final ActionDecider decider = new ActionDecider(20);

    private static Finding f(Item item) {
        return new Finding(item, 0, 1, "v");
    }

    private static List<Finding> phones(int n) {
        List<Finding> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Finding(Item.PHONE, i * 20, i * 20 + 13, "010-1234-" + (1000 + i)));
        }
        return list;
    }

    @Test
    @DisplayName("AC-3.3 아무것도 없으면 허용")
    void nothingIsAllow() {
        assertThat(decider.decide(List.of())).isEqualTo(Action.ALLOW);
    }

    @Test
    @DisplayName("D1 개인정보와 D3 코드명, 고객사는 가림")
    void personalAndDictionaryAreMask() {
        assertThat(decider.decide(List.of(f(Item.PHONE)))).isEqualTo(Action.MASK);
        assertThat(decider.decide(List.of(f(Item.CODENAME), f(Item.CLIENT)))).isEqualTo(Action.MASK);
    }

    @Test
    @DisplayName("D2 자격증명과 D3 등급 표시는 차단이고, 다른 탐지와 섞여도 가장 강한 차단을 따른다(AC-3.1)")
    void secretsAndClassifiedAreBlock() {
        assertThat(decider.decide(List.of(f(Item.PASSWORD)))).isEqualTo(Action.BLOCK);
        assertThat(decider.decide(List.of(f(Item.CLASSIFIED)))).isEqualTo(Action.BLOCK);
        List<Finding> mixed = new ArrayList<>(phones(25));
        mixed.add(f(Item.API_KEY));
        assertThat(decider.decide(mixed)).isEqualTo(Action.BLOCK);
    }

    @Test
    @DisplayName("AC-3.2 D1 값이 대량 기준(20개) 이상이면 경고, 하나 모자라면 가림")
    void bulkPersonalIsWarn() {
        assertThat(decider.decide(phones(19))).isEqualTo(Action.MASK);
        assertThat(decider.decide(phones(20))).isEqualTo(Action.WARN);
    }

    @Test
    @DisplayName("대량 기준은 D1만 센다: 코드명이 많아도 경고가 아니다")
    void bulkCountsOnlyPersonal() {
        List<Finding> many = new ArrayList<>(phones(19));
        many.add(f(Item.CODENAME));
        assertThat(decider.decide(many)).isEqualTo(Action.MASK);
    }
}
