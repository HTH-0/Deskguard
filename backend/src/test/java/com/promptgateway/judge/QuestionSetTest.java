package com.promptgateway.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionSetTest {

    static final List<String> IDS = List.of("I1", "I2a", "I2b", "I3", "I4", "F1", "F2", "F3", "F4a", "F4b");

    private static String tsv(List<String> ids) {
        return ids.stream()
                .map(id -> id + "\t질문 " + id + "\t참 " + id + "\t거짓 " + id)
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("AC-3.9 질문 10개를 순서대로 읽고 네 칸(ID, 질문, 참 기준, 거짓 기준)을 채운다")
    void parsesTenQuestions() {
        QuestionSet set = QuestionSet.parse(tsv(IDS));

        assertThat(set.ids()).containsExactlyElementsOf(IDS);
        Question first = set.questions().get(0);
        assertThat(first.id()).isEqualTo("I1");
        assertThat(first.instruction()).isEqualTo("질문 I1");
        assertThat(first.trueCriteria()).isEqualTo("참 I1");
        assertThat(first.falseCriteria()).isEqualTo("거짓 I1");
    }

    @Test
    @DisplayName("줄바꿈이 CRLF여도 읽는다")
    void acceptsCrlf() {
        QuestionSet set = QuestionSet.parse(tsv(IDS).replace("\n", "\r\n") + "\r\n");

        assertThat(set.ids()).containsExactlyElementsOf(IDS);
    }

    @Test
    @DisplayName("질문이 10개보다 적으면 거부한다")
    void rejectsTooFewQuestions() {
        assertThatThrownBy(() -> QuestionSet.parse(tsv(IDS.subList(0, 9))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F4b");
    }

    @Test
    @DisplayName("정해진 ID가 아닌 질문이 있으면 거부한다")
    void rejectsUnknownId() {
        List<String> ids = new java.util.ArrayList<>(IDS);
        ids.set(9, "X9");

        assertThatThrownBy(() -> QuestionSet.parse(tsv(ids))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ID가 중복되면 거부한다")
    void rejectsDuplicateId() {
        List<String> ids = new java.util.ArrayList<>(IDS);
        ids.set(9, "I1");

        assertThatThrownBy(() -> QuestionSet.parse(tsv(ids))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비어 있는 칸이 있으면 거부한다")
    void rejectsBlankField() {
        String broken = tsv(IDS).replace("거짓 F2", " ");

        assertThatThrownBy(() -> QuestionSet.parse(broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("F2");
    }

    @Test
    @DisplayName("칸이 4개가 아닌 줄이 있으면 거부한다")
    void rejectsWrongColumnCount() {
        String broken = tsv(IDS).replace("\t거짓 I3", "");

        assertThatThrownBy(() -> QuestionSet.parse(broken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("I3");
    }

    @Test
    @DisplayName("실제 질문 파일(judge/questions.tsv)에 질문 10개가 정해진 ID 순서로 있다")
    void realResourceHasTenQuestions() {
        QuestionSet set = QuestionSet.fromClasspath("judge/questions.tsv");

        assertThat(set.ids()).containsExactlyElementsOf(IDS);
    }
}
