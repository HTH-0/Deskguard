package com.promptgateway.judge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 판정 질문 10개를 파일에서 읽는다. 질문 문구가 바뀌어도 코드는 그대로이다.
 * 파일은 탭으로 나눈 네 칸(ID, 질문, 참 기준, 거짓 기준)이며, 문구의 원본은 설계서 1.3절이다.
 * 정해진 ID 10개가 모두 있고 빈 칸이 없어야 하며, 어긋나면 서버가 시작될 때 실패한다.
 */
public class QuestionSet {

    public static final List<String> EXPECTED_IDS =
            List.of("I1", "I2a", "I2b", "I3", "I4", "F1", "F2", "F3", "F4a", "F4b");

    private final List<Question> questions;

    private QuestionSet(List<Question> questions) {
        this.questions = List.copyOf(questions);
    }

    public static QuestionSet fromClasspath(String path) {
        try (InputStream in = QuestionSet.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("질문 파일을 찾을 수 없습니다: " + path);
            }
            return read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalArgumentException("질문 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    public static QuestionSet parse(String tsv) {
        try {
            return read(new BufferedReader(new StringReader(tsv)));
        } catch (IOException e) {
            throw new IllegalArgumentException("질문 파일을 읽을 수 없습니다.", e);
        }
    }

    private static QuestionSet read(BufferedReader reader) throws IOException {
        List<Question> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] c = line.split("\t", -1);
            String id = c[0].trim();
            if (c.length != 4) {
                throw new IllegalArgumentException("질문 " + id + "의 칸이 4개가 아닙니다.");
            }
            if (!EXPECTED_IDS.contains(id)) {
                throw new IllegalArgumentException("정해지지 않은 질문 ID입니다: " + id);
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("질문 ID가 중복되었습니다: " + id);
            }
            for (int i = 1; i < 4; i++) {
                if (c[i].isBlank()) {
                    throw new IllegalArgumentException("질문 " + id + "에 비어 있는 칸이 있습니다.");
                }
            }
            list.add(new Question(id, c[1].trim(), c[2].trim(), c[3].trim()));
        }
        for (String expected : EXPECTED_IDS) {
            if (!seen.contains(expected)) {
                throw new IllegalArgumentException("질문 " + expected + "이(가) 없습니다.");
            }
        }
        return new QuestionSet(list);
    }

    public List<Question> questions() {
        return questions;
    }

    public List<String> ids() {
        return questions.stream().map(Question::id).toList();
    }
}
