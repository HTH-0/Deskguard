package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 평가 세트용 가짜 값 생성기(backend/scripts/fake_values.py)가 만든 값을 Java 탐지기가 같은 항목으로 잡는지 교차 확인한다.
 * 표본은 `python fake_values.py > ../src/test/resources/fake_values_sample.tsv`로 다시 만든다(seed 고정).
 * 이 시험이 깨지면 생성기와 탐지 규칙이 어긋난 것이므로, 평가 세트를 만들기 전에 고친다.
 */
class FakeValuesCrossCheckTest {

    final DetectionEngine engine = new DetectionEngine(
            List.of(new PersonalDataDetector(), new SecretDetector(), new DictionaryDetector(List.of(), List.of(), List.of())),
            Set.of());

    @Test
    @DisplayName("생성기가 만든 360개 값을 모두 해당 항목 하나로 탐지한다")
    void everyGeneratedValueIsDetectedAsItsItem() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/fake_values_sample.tsv"), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(360);

        for (String line : lines) {
            String[] cols = line.split("\t", 2);
            List<Finding> found = engine.detect("값: " + cols[1] + " 정리해 줘");
            assertThat(found).as(line).extracting(Finding::item).containsExactly(Item.valueOf(cols[0]));
        }
    }
}
