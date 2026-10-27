package com.promptgateway.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidatorTest {

    private final InputValidator validator = new InputValidator(1000);

    @ParameterizedTest(name = "AC-1.1 비어 있거나 공백뿐인 입력은 거부한다: [{0}]")
    @ValueSource(strings = {"", " ", "   ", "\n", "\t \n"})
    void rejectsEmptyOrBlank(String prompt) {
        assertThatThrownBy(() -> validator.validate(prompt)).isInstanceOf(InvalidPromptException.class);
    }

    @Test
    @DisplayName("AC-1.2 prompt가 없으면(null) 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidPromptException.class);
    }

    @Test
    @DisplayName("AC-1.3 1,000자는 허용한다")
    void allowsExactlyMaxLength() {
        assertThatCode(() -> validator.validate("가".repeat(1000))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AC-1.3 1,001자부터 거부한다")
    void rejectsOverMaxLength() {
        assertThatThrownBy(() -> validator.validate("가".repeat(1001))).isInstanceOf(InvalidPromptException.class);
    }

    @Test
    @DisplayName("글자 수는 유니코드 문자 단위로 센다 (이모지 1,000개는 허용, 1,001개는 거부)")
    void countsCodePointsNotChars() {
        assertThatCode(() -> validator.validate("😀".repeat(1000))).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate("😀".repeat(1001))).isInstanceOf(InvalidPromptException.class);
    }

    @Test
    @DisplayName("정상 문장은 통과한다")
    void allowsNormalPrompt() {
        assertThatCode(() -> validator.validate("연차 신청은 어떻게 하나요?")).doesNotThrowAnyException();
    }
}
