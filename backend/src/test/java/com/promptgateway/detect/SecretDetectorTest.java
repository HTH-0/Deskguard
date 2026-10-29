package com.promptgateway.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D2 자격증명 탐지(AC-2.3, 2.2). 키는 형식만 맞춘 가짜 값이다. */
class SecretDetectorTest {

    final SecretDetector detector = new SecretDetector();

    private List<Item> items(String text) {
        return detector.detect(text).stream().map(Finding::item).toList();
    }

    @Test
    @DisplayName("AC-2.3 알려진 키 형식(AWS, Google, OpenAI 형식, GitHub, Slack)을 찾는다")
    void apiKeys() {
        assertThat(items("aws_access_key_id = AKIAQ7XRTPLM3NBV2W8K")).contains(Item.API_KEY);
        assertThat(items("key AIzaSyB3k9Xq7LmN2pQr8sT4uV6wX0yZ1aBcDeF")).containsExactly(Item.API_KEY);
        assertThat(items("OPENAI sk-proj-4fG7hJ9kL2mN5pQ8rS1tV3wX6yZ")).containsExactly(Item.API_KEY);
        assertThat(items("token ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8")).containsExactly(Item.API_KEY);
        assertThat(items("slack xoxb-1234567890-abcdefghij")).containsExactly(Item.API_KEY);
    }

    @Test
    @DisplayName("AC-2.3 개인 키 블록, 접속 문자열, JWT를 찾는다")
    void otherSecrets() {
        assertThat(items("-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA\n-----END RSA PRIVATE KEY-----"))
                .containsExactly(Item.PRIVATE_KEY);
        assertThat(items("url: postgres://app_user:S3cr3tPw9@db.internal:5432/orders")).containsExactly(Item.CONN_STRING);
        assertThat(items("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .containsExactly(Item.JWT);
    }

    @Test
    @DisplayName("AC-2.3 비밀번호 할당 표시 뒤의 값을 찾는다(영어와 한국어)")
    void passwords() {
        assertThat(items("DB_PASSWORD=Qw7!zLp92x")).containsExactly(Item.PASSWORD);
        assertThat(items("서버 비밀번호: hanA2024!@")).containsExactly(Item.PASSWORD);
        assertThat(items("비번 = gaon#7712")).containsExactly(Item.PASSWORD);
    }

    @ParameterizedTest(name = "AC-2.2 자리 표시자와 설명은 찾지 않는다: {0}")
    @ValueSource(strings = {"password=****", "password: <your_password>", "sk-xxxxxxxxxxxxxxxxxxxxxxxx", "AKIAXXXXXXXXXXXXXXXX",
            "postgres://user:password@localhost/db", "비밀번호는 8자 이상이어야 해?", "API 키는 환경변수에 넣어야 해?",
            "password=your_password_here"})
    void placeholders(String text) {
        assertThat(items(text)).isEmpty();
    }

    @Test
    @DisplayName("흔한 약한 비밀번호는 자리 표시자 단어를 포함해도 찾는다 (자리 표시자는 값 전체가 일치할 때만 뺀다)")
    void weakRealPasswordsAreFound() {
        assertThat(items("DB_PASSWORD=Password1!")).containsExactly(Item.PASSWORD);
        assertThat(items("비밀번호: secret2024!")).containsExactly(Item.PASSWORD);
    }
}
