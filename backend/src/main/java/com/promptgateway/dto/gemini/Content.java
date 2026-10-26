package com.promptgateway.dto.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * candidates[0].content 부분과, 우리가 보낼 요청의 contents[0] 부분에 둘 다 쓰인다.
 *
 * - 요청을 보낼 때는 parts만 채우고 role은 안 써도 되므로 null로 둔다.
 * - @JsonInclude(NON_NULL)을 붙여두면, role이 null일 때 JSON에 "role":null 을 아예 안 쓰고
 *   생략해버린다. (안 붙이면 매번 "role":null 이 껴서 보내지는데, 없어도 되는 값이라 깔끔하게 뺀 것)
 * - 응답을 받을 때는 Gemini가 role:"model" 을 채워서 주는데, 그건 그냥 참고용이라 우리 로직에서는
 *   안 쓰고 parts만 꺼내 쓴다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Content(List<Part> parts, String role) {

    // 요청 보낼 때 편하게 쓰려고 만든 보조 생성자: role 없이 parts만 넘기면 role은 자동으로 null
    public static Content of(String text) {
        return new Content(List.of(new Part(text)), null);
    }
}
