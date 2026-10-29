package com.promptgateway.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * D3 회사 기밀 탐지. 회사 사전(코드명, 고객사, 등급 표시)과 대소문자 구분 없이 일치하는 곳을 찾는다.
 * 사전은 설정 파일에서 읽는다(docs/dummy-company.md). 사전에 없는 기밀(문맥 판정)은 범위 밖이다.
 */
public class DictionaryDetector implements Detector {

    private record Entry(Item item, String word) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public DictionaryDetector(List<String> codenames, List<String> clients, List<String> classified) {
        codenames.forEach(w -> add(Item.CODENAME, w));
        clients.forEach(w -> add(Item.CLIENT, w));
        classified.forEach(w -> add(Item.CLASSIFIED, w));
    }

    private void add(Item item, String word) {
        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("회사 사전에 빈 단어가 있습니다: " + item);
        }
        entries.add(new Entry(item, word.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<Finding> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<Finding> found = new ArrayList<>();
        for (Entry e : entries) {
            int from = 0;
            int at;
            while ((at = lower.indexOf(e.word(), from)) >= 0) {
                int end = at + e.word().length();
                found.add(new Finding(e.item(), at, end, text.substring(at, end)));
                from = end;
            }
        }
        return found;
    }
}
