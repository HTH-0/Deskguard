package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D1 개인정보 탐지(AC-2.1, 2.2, 2.6). 값은 모두 가짜이다. 카드번호 4111 1111 1111 1111은 널리 쓰이는 시험용 번호이다.
 */
class PersonalDataDetectorTest {

    final PersonalDataDetector detector = new PersonalDataDetector();

    private List<Item> items(String text) {
        return detector.detect(text).stream().map(Finding::item).toList();
    }

    @Test
    @DisplayName("AC-2.1 주민등록번호와 외국인등록번호를 생년월일과 뒷자리 첫 숫자로 구분해 찾는다(하이픈 없어도)")
    void residentNumbers() {
        assertThat(items("본인 주민번호 900101-1234567 입니다")).containsExactly(Item.RRN);
        assertThat(items("주민번호 9001012234567")).containsExactly(Item.RRN);
        assertThat(items("외국인 직원 880315-5123456")).containsExactly(Item.FRN);
    }

    @ParameterizedTest(name = "AC-2.2 검증에 실패하는 주민번호 모양은 찾지 않는다: {0}")
    @ValueSource(strings = {"000000-0000000", "901301-1234567", "900230-1234567", "900101-9234567", "1900101-1234567"})
    void invalidResidentNumbers(String text) {
        assertThat(items(text)).isEmpty();
    }

    @Test
    @DisplayName("AC-2.1 휴대전화를 하이픈, 점, 공백, 붙여 쓰기로 찾고 위치와 값을 돌려준다")
    void phones() {
        String text = "연락처 010-2345-6789, 010.9876.5432, 011 234 5678, 01055551234";
        List<Finding> found = detector.detect(text);

        assertThat(found).extracting(Finding::item).containsOnly(Item.PHONE).hasSize(4);
        assertThat(found.getFirst().value()).isEqualTo("010-2345-6789");
        assertThat(text.substring(found.getFirst().start(), found.getFirst().end())).isEqualTo("010-2345-6789");
    }

    @ParameterizedTest(name = "AC-2.2 예시 전화번호는 찾지 않는다: {0}")
    @ValueSource(strings = {"010-0000-0000", "010-1111-1111", "02-555-0100 대표번호"})
    void examplePhones(String text) {
        assertThat(items(text)).isEmpty();
    }

    @Test
    @DisplayName("AC-2.1 이메일을 찾되 example.com 같은 예시 도메인은 뺀다")
    void emails() {
        assertThat(items("김민수 minsu.kim@mail.example 로 보내 줘")).containsExactly(Item.EMAIL);
        assertThat(items("형식 예: user@example.com")).isEmpty();
    }

    @Test
    @DisplayName("AC-2.1 계좌번호는 은행명이나 '계좌' 뒤에 나올 때만 찾는다")
    void accounts() {
        assertThat(items("국민은행 123-456789-01-234 로 입금")).containsExactly(Item.ACCOUNT);
        assertThat(items("환불 계좌: 3333-01-2345678")).containsExactly(Item.ACCOUNT);
        assertThat(items("주문번호 123-456789-01-234 확인 부탁")).isEmpty();
    }

    @Test
    @DisplayName("AC-2.1, 2.2 카드번호는 Luhn 검사를 통과할 때만 찾는다")
    void cards() {
        assertThat(items("카드 4111 1111 1111 1111 결제")).containsExactly(Item.CARD);
        assertThat(items("카드 4111-1111-1111-1111")).containsExactly(Item.CARD);
        assertThat(items("카드 4111 1111 1111 1112")).isEmpty();
    }

    @ParameterizedTest(name = "AC-2.6 값 없이 형식만 묻는 문장은 아무것도 찾지 않는다: {0}")
    @ValueSource(strings = {"주민등록번호는 몇 자리야?", "휴대전화 번호 형식 검증 정규식 알려줘", "카드번호 Luhn 검사가 뭐야",
            "회의는 2024-10-29 14:00에 시작", "버전 1.2.3, 빌드 20241029"})
    void formatQuestions(String text) {
        assertThat(items(text)).isEmpty();
    }
}
