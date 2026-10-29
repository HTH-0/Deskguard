package com.promptgateway.mask;

import com.promptgateway.detect.DetectionEngine;
import com.promptgateway.detect.DictionaryDetector;
import com.promptgateway.detect.Finding;
import com.promptgateway.detect.PersonalDataDetector;
import com.promptgateway.detect.SecretDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 가림과 복원(AC-4.1~4.5, docs/design.md 4장). 값은 모두 가짜이다. */
class MaskerTest {

    final DetectionEngine engine = new DetectionEngine(
            List.of(new PersonalDataDetector(), new SecretDetector(),
                    new DictionaryDetector(List.of("블루웨일"), List.of("한결물산"), List.of("대외비"))), Set.of());
    final Masker masker = new Masker();

    private MaskResult mask(String text) {
        List<Finding> found = engine.detect(text);
        return masker.mask(text, found);
    }

    @Test
    @DisplayName("AC-4.1 탐지한 값을 [항목+순번] 가명으로 바꾸고, 원래 값은 하나도 남지 않는다")
    void replacesAllValues() {
        String text = "김민수 010-2345-6789, minsu.k@mail.example / 한결물산 블루웨일 건";
        MaskResult r = mask(text);

        assertThat(r.maskedText()).isEqualTo("김민수 [전화1], [이메일1] / [고객사1] [코드명1] 건");
        assertThat(r.maskedText()).doesNotContain("010-2345-6789", "minsu.k@mail.example", "한결물산", "블루웨일");
        assertThat(r.pseudonymToValue()).containsEntry("[전화1]", "010-2345-6789");
    }

    @Test
    @DisplayName("AC-4.2 같은 값은 같은 가명(하이픈 유무가 달라도 같은 값), 다른 값은 다른 가명")
    void sameValueSamePseudonym() {
        MaskResult r = mask("010-2345-6789 와 01023456789 그리고 010-9876-5432");

        assertThat(r.maskedText()).isEqualTo("[전화1] 와 [전화1] 그리고 [전화2]");
    }

    @Test
    @DisplayName("AC-4.5 원문에 이미 [전화1] 같은 글자가 있으면 그 순번을 건너뛴다")
    void avoidsExistingPseudonymText() {
        MaskResult r = mask("템플릿의 [전화1] 자리에 010-2345-6789 넣어 줘");

        assertThat(r.maskedText()).isEqualTo("템플릿의 [전화1] 자리에 [전화2] 넣어 줘");
        assertThat(r.pseudonymToValue()).containsOnlyKeys("[전화2]");
    }

    @Test
    @DisplayName("AC-4.3 응답의 가명을 원래 값으로 되돌리고, 되돌린 수를 센다")
    void restores() {
        MaskResult r = mask("010-2345-6789, 010-9876-5432 표로 정리");

        RestoreResult out = masker.restore("| 번호 |\n| [전화1] |\n| [전화2] |\n| [전화1] |", r);

        assertThat(out.text()).isEqualTo("| 번호 |\n| 010-2345-6789 |\n| 010-9876-5432 |\n| 010-2345-6789 |");
        assertThat(out.restored()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC-4.3 외부 AI가 가명을 조금 바꿔 써도(공백, 전각 괄호) 되돌리고, 대응표에 없는 가명 모양은 그대로 둔다")
    void restoresVariantsAndKeepsUnknown() {
        MaskResult r = mask("010-2345-6789 연락");

        RestoreResult out = masker.restore("[전화 1] 또는 ［전화1］ 로 연락, [전화9]는 없음", r);

        assertThat(out.text()).isEqualTo("010-2345-6789 또는 010-2345-6789 로 연락, [전화9]는 없음");
        assertThat(out.restored()).isEqualTo(2);
    }

    @Test
    @DisplayName("탐지가 없으면 문장을 그대로 두고 대응표가 비어 있다")
    void nothingToMask() {
        MaskResult r = mask("회의록 요약해 줘");

        assertThat(r.maskedText()).isEqualTo("회의록 요약해 줘");
        assertThat(r.pseudonymToValue()).isEmpty();
    }

    @Test
    @DisplayName("대응표는 요청마다 따로이다: 다른 요청의 가명은 되돌리지 않는다 (AC-4.4)")
    void mappingsAreIsolated() {
        MaskResult first = mask("010-2345-6789");
        MaskResult second = mask("010-9876-5432");

        assertThat(masker.restore("[전화1]", second).text()).isEqualTo("010-9876-5432");
        assertThat(masker.restore("[전화1]", first).text()).isEqualTo("010-2345-6789");
    }
}
