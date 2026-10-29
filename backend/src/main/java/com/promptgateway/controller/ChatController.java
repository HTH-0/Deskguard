package com.promptgateway.controller;

import com.promptgateway.dto.BlockedResponse;
import com.promptgateway.dto.ChatRequest;
import com.promptgateway.dto.ChatResponse;
import com.promptgateway.dto.MeasurementResponse;
import com.promptgateway.gateway.ChatGateway;
import com.promptgateway.gateway.GatewayResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;

/**
 * 사용자(또는 Postman)가 실제로 요청을 보내는 창구. 요청을 받아 ChatGateway에 맡기고,
 * 돌아온 결과를 HTTP 응답으로 바꾸는 일만 한다. 검사와 판단은 하지 않는다.
 *
 *   통과  -> 200 {"answer": ...}
 *   차단  -> 403 {"blocked": true, "message": ...}   (차단 이유와 단계는 응답에 싣지 않는다)
 *   입력 검증 실패 -> 400 (GlobalExceptionHandler가 변환)
 *
 * 측정 응답은 차단 이유를 드러내므로 공격자에게는 우회 방법을 알려주는 수단이 된다. 그래서 측정 모드가 켜져 있어도
 * 같은 컴퓨터(127.0.0.1, ::1)에서 온 요청에만 준다(AC-9.7). 주소는 X-Forwarded-For 같은 헤더(조작 가능)가 아니라
 * 실제 TCP 접속 주소(getRemoteAddr)로 판단한다. application.yml의 server.forward-headers-strategy=none이 이를 보장한다(AC-9.8).
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatGateway gateway;

    public ChatController(ChatGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, HttpServletRequest http) {
        boolean measure = gateway.isMeasurementMode() && isLocal(http.getRemoteAddr());
        GatewayResult result = gateway.handle(request.prompt(), measure);

        // 측정 모드(개발용): 통과와 차단 모두 200과 같은 형식으로 판정 결과를 돌려준다.
        if (measure) {
            return ResponseEntity.ok(MeasurementResponse.from(result));
        }

        if (result.decision() == GatewayResult.Decision.BLOCKED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BlockedResponse.INSTANCE);
        }
        return ResponseEntity.ok(new ChatResponse(result.answer()));
    }

    /** 접속 주소가 같은 컴퓨터(루프백)인가. 주소를 해석할 수 없으면 외부로 본다. */
    private static boolean isLocal(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) {
            return false;
        }
        try {
            // getRemoteAddr()는 IP 문자열이므로 DNS 조회 없이 해석된다
            return InetAddress.ofLiteral(remoteAddr).isLoopbackAddress();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
