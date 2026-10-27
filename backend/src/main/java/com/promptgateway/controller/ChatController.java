package com.promptgateway.controller;

import com.promptgateway.dto.BlockedResponse;
import com.promptgateway.dto.ChatRequest;
import com.promptgateway.dto.ChatResponse;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.GatewayResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자(또는 Postman)가 실제로 요청을 보내는 창구. 요청을 받아 ChatGateway에 맡기고,
 * 돌아온 결과를 HTTP 응답으로 바꾸는 일만 한다. 검사와 판단은 하지 않는다.
 *
 *   통과  -> 200 {"answer": ...}
 *   차단  -> 403 {"blocked": true, "message": ...}   (차단 이유와 단계는 응답에 싣지 않는다)
 *   입력 검증 실패 -> 400 (GlobalExceptionHandler가 변환)
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatGateway gateway;

    public ChatController(ChatGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        GatewayResult result = gateway.handle(request.prompt());

        if (result.decision() == GatewayResult.Decision.BLOCKED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BlockedResponse.INSTANCE);
        }
        return ResponseEntity.ok(new ChatResponse(result.answer()));
    }
}
