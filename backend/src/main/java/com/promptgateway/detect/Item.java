package com.promptgateway.detect;

/**
 * 탐지 항목. label은 가명에 쓰는 한글 이름이다(예: [전화1]). 외부 AI가 무엇이었는지 짐작해 답할 수 있게 항목을 드러낸다.
 * 순서는 겹친 탐지를 정리할 때 같은 범주 안의 우선순위이다(docs/design.md 2장).
 */
public enum Item {
    RRN(Category.D1, "주민번호"),
    FRN(Category.D1, "외국인번호"),
    CARD(Category.D1, "카드"),
    PHONE(Category.D1, "전화"),
    ACCOUNT(Category.D1, "계좌"),
    EMAIL(Category.D1, "이메일"),
    PRIVATE_KEY(Category.D2, "개인키"),
    CONN_STRING(Category.D2, "접속정보"),
    API_KEY(Category.D2, "API키"),
    JWT(Category.D2, "토큰"),
    PASSWORD(Category.D2, "비밀번호"),
    CLASSIFIED(Category.D3, "등급표시"),
    CODENAME(Category.D3, "코드명"),
    CLIENT(Category.D3, "고객사");

    private final Category category;
    private final String label;

    Item(Category category, String label) {
        this.category = category;
        this.label = label;
    }

    public Category category() {
        return category;
    }

    public String label() {
        return label;
    }
}
