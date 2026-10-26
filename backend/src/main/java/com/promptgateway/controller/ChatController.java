package com.promptgateway.controller;

import com.promptgateway.dto.ChatRequest;
import com.promptgateway.dto.ChatResponse;
import com.promptgateway.service.GeminiService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자(또는 Postman)가 실제로 요청을 보내는 창구.
 * @RestController: 이 클래스의 메서드가 리턴하는 객체를 JSON으로 자동 변환해서 응답으로 보내준다.
 *                   (@Controller + @ResponseBody 를 합친 것)
 *
 * 지금은 필터가 없어서 요청을 그대로 GeminiService에 넘기지만, 4단계부터는
 * 여기(혹은 서비스 계층)에 "차단할지 통과시킬지" 판단하는 코드가 끼어들게 된다.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String answer = geminiService.generateAnswer(request.prompt());
        return new ChatResponse(answer);
    }
}
