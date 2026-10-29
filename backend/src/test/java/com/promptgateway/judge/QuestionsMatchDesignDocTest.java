package com.promptgateway.judge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 설계서 열린 항목 O3: 코드가 읽는 질문 파일이 설계서 1.3절(v2)의 표와 같은 문구인지 자동으로 확인한다.
 * 설계서 파일은 저장소의 docs 폴더에 있으므로 backend 폴더에서 실행할 때 상대 경로로 찾는다.
 * 저장소 밖에서 실행하는 경우(파일이 없는 경우)에는 건너뛴다.
 */
class QuestionsMatchDesignDocTest {

    private static final Path DESIGN = Path.of("..", "docs", "archive", "design-v1-hr.md");
    private static final Pattern ROW = Pattern.compile("^\\| (I1|I2a|I2b|I3|I4|F1|F2|F3|F4a|F4b) \\|.*");

    @Test
    @DisplayName("질문 파일의 모든 문구가 설계서 1.3절 표와 같다")
    void resourceMatchesDesignSection13() throws IOException {
        assumeTrue(Files.exists(DESIGN), "설계서 파일이 없어 건너뜀");

        List<String> lines = Files.readAllLines(DESIGN, StandardCharsets.UTF_8);
        List<Question> fromDoc = new ArrayList<>();
        boolean inSection = false;
        for (String line : lines) {
            if (line.startsWith("### 1.3")) {
                inSection = true;
                continue;
            }
            if (line.startsWith("## ")) {
                inSection = false;
            }
            Matcher m = ROW.matcher(line);
            if (inSection && m.matches()) {
                String[] c = line.split("\\|");
                fromDoc.add(new Question(c[1].trim(), c[2].trim(), c[3].trim(), c[4].trim()));
            }
        }

        QuestionSet fromResource = QuestionSet.fromClasspath("judge/questions.tsv");

        assertThat(fromDoc).hasSize(10);
        assertThat(fromResource.questions()).containsExactlyElementsOf(fromDoc);
    }
}
