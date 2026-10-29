package com.promptgateway.gateway;

import com.promptgateway.detect.Action;
import com.promptgateway.detect.FindingCount;
import com.promptgateway.detect.Item;
import com.promptgateway.log.GatewayLogger;
import com.promptgateway.support.TestGateways;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 게이트웨이의 처리 흐름(명세 v2 1장, FR-3~6). 가짜 외부 AI가 받은 문장을 기록해서, 무엇이 밖으로 나갔는지 직접 확인한다.
 * 가짜 외부 AI는 받은 문장을 그대로 답에 넣어 돌려준다(가명이 답에 들어가는 상황을 흉내 낸다).
 */
class GatewayFlowTest {

    List<String> sentToAi;
    List<String> logLines;
    ChatGateway gateway;

    @BeforeEach
    void setUp() {
        sentToAi = new ArrayList<>();
        logLines = new ArrayList<>();
        gateway = TestGateways.gateway(prompt -> {
            sentToAi.add(prompt);
            return "정리 결과: " + prompt;
        }, new GatewayLogger(logLines::add), false);
    }

    @Test
    @DisplayName("AC-3.3 탐지가 없으면 원문 그대로 외부 AI에 보내고 답을 돌려준다")
    void allow() {
        GatewayResult r = gateway.handle("회의록 요약해 줘", false, false);

        assertThat(r.action()).isEqualTo(Action.ALLOW);
        assertThat(sentToAi).containsExactly("회의록 요약해 줘");
        assertThat(r.answer()).isEqualTo("정리 결과: 회의록 요약해 줘");
        assertThat(r.counts()).isEmpty();
    }

    @Test
    @DisplayName("AC-4.1, 6.1 가림: 외부 AI에는 가명만 가고, 직원은 원래 값으로 되돌린 답과 범주별 개수를 받는다")
    void mask() {
        GatewayResult r = gateway.handle("김민수 010-2345-6789, 한결물산 담당자 표로 정리", false, false);

        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(sentToAi).containsExactly("김민수 [전화1], [고객사1] 담당자 표로 정리");
        assertThat(sentToAi.getFirst()).doesNotContain("010-2345-6789", "한결물산");
        assertThat(r.answer()).isEqualTo("정리 결과: 김민수 010-2345-6789, 한결물산 담당자 표로 정리");
        assertThat(r.counts()).extracting(FindingCount::item, FindingCount::count)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(Item.PHONE, 1), org.assertj.core.groups.Tuple.tuple(Item.CLIENT, 1));
        assertThat(r.restored()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-5.1, 6.3 차단: 자격증명이 있으면 외부 AI를 부르지 않는다")
    void block() {
        GatewayResult r = gateway.handle("이 설정 왜 안 돼? DB_PASSWORD=Qw7!zLp92x, 담당 010-2345-6789", false, false);

        assertThat(r.action()).isEqualTo(Action.BLOCK);
        assertThat(sentToAi).isEmpty();
        assertThat(r.answer()).isNull();
        assertThat(r.counts()).extracting(FindingCount::item).contains(Item.PASSWORD, Item.PHONE);
    }

    @Test
    @DisplayName("AC-6.2 경고: 확인 전에는 보내지 않고, 확인하면 가림 처리로 보낸다(원문으로 보내지 않는다)")
    void warnThenConfirm() {
        String list = TestGateways.phoneList(20);

        GatewayResult first = gateway.handle(list, false, false);
        assertThat(first.action()).isEqualTo(Action.WARN);
        assertThat(sentToAi).isEmpty();

        GatewayResult confirmed = gateway.handle(list, true, false);
        assertThat(confirmed.action()).isEqualTo(Action.MASK);
        assertThat(confirmed.confirmed()).isTrue();
        assertThat(sentToAi).hasSize(1);
        assertThat(sentToAi.getFirst()).doesNotContain("010-2000-3000").contains("[전화1]", "[전화20]");
        assertThat(confirmed.answer()).contains("010-2000-3000", "010-2019-3019");
    }

    @Test
    @DisplayName("확인 표시는 경고가 아닌 요청의 처리를 바꾸지 않는다(차단은 확인해도 차단)")
    void confirmDoesNotUnblock() {
        GatewayResult r = gateway.handle("password=Qw7!zLp92x", true, false);

        assertThat(r.action()).isEqualTo(Action.BLOCK);
        assertThat(sentToAi).isEmpty();
    }

    @Test
    @DisplayName("AC-9.2 측정 처리: 외부 AI를 부르지 않고 가린 문장과 탐지 목록, 지연을 돌려준다")
    void measurement() {
        GatewayResult r = gateway.handle("김민수 010-2345-6789", false, true);

        assertThat(sentToAi).isEmpty();
        assertThat(r.action()).isEqualTo(Action.MASK);
        assertThat(r.maskedPrompt()).isEqualTo("김민수 [전화1]");
        assertThat(r.findings()).extracting(f -> f.item()).containsExactly(Item.PHONE);
        assertThat(r.timings().detectMs()).isNotNegative();
    }

    @Test
    @DisplayName("AC-9.4 측정 처리: 가림 처리된 문장은 가린 문장을 그대로 복원해 복원 지연을 채우고, 복원한 문장은 원문과 같다")
    void measurementRestores() {
        GatewayResult r = gateway.handle("김민수 010-2345-6789, 한결물산 담당자", false, true);

        assertThat(sentToAi).isEmpty();
        assertThat(r.timings().restoreMs()).isNotNull().isNotNegative();
        assertThat(r.answer()).isEqualTo("김민수 010-2345-6789, 한결물산 담당자");
        assertThat(r.restored()).isEqualTo(2);
    }

    @Test
    @DisplayName("AC-9.4 측정 처리: 가림이 없는 요청은 복원 지연이 없다")
    void measurementNoRestoreWhenNotMasked() {
        assertThat(gateway.handle("회의록 요약해 줘", false, true).timings().restoreMs()).isNull();
        assertThat(gateway.handle("password=Sup3rS3cret!", false, true).timings().restoreMs()).isNull();
    }

    @Test
    @DisplayName("AC-1.1 입력 검증 실패는 외부 AI를 부르지 않고 예외로 알리며 로그를 한 줄 남긴다")
    void invalidInput() {
        assertThatThrownBy(() -> gateway.handle("   ", false, false)).isInstanceOf(InvalidPromptException.class);

        assertThat(sentToAi).isEmpty();
        assertThat(logLines).singleElement().asString().contains("action=ERROR", "stage=VALIDATION");
    }

    @Test
    @DisplayName("AC-5.2 외부 AI 오류는 그대로 밖으로 알리고 로그를 한 줄 남긴다")
    void answerFailure() {
        ChatGateway failing = TestGateways.gateway(p -> {
            throw new IllegalStateException("외부 오류");
        }, new GatewayLogger(logLines::add), false);

        assertThatThrownBy(() -> failing.handle("회의록 요약해 줘", false, false)).isInstanceOf(IllegalStateException.class);
        assertThat(logLines).singleElement().asString().contains("action=ERROR", "stage=ANSWER");
    }

    @Test
    @DisplayName("AC-7.1 처리마다 로그가 정확히 한 줄 남는다")
    void oneLogLinePerRequest() {
        gateway.handle("회의록 요약해 줘", false, false);
        gateway.handle("010-2345-6789 정리", false, false);
        gateway.handle("password=Qw7!zLp92x", false, false);
        gateway.handle(TestGateways.phoneList(20), false, false);

        assertThat(logLines).hasSize(4);
        assertThat(logLines).extracting(l -> l.replaceAll(".*action=(\\w+).*", "$1"))
                .containsExactly("ALLOW", "MASK", "BLOCK", "WARN");
    }
}
