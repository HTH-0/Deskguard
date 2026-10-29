package com.promptgateway.controller;

import com.promptgateway.detect.Action;
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
 * 직원(포털)이 요청을 보내는 창구. 요청을 ChatGateway에 맡기고 결과를 HTTP 응답으로 바꾼다(design.md 5장).
 *
 *   허용, 가림       -> 200 {action, answer, findings}
 *   경고(확인 전)     -> 200 {action: WARN, findings, confirmRequired: true, message}
 *   차단            -> 403 {action: BLOCK, findings, message}
 *   입력 검증 실패    -> 400 (GlobalExceptionHandler)
 *
 * 측정 응답은 탐지 위치와 가린 문장을 드러내므로, 측정 모드가 켜져 있어도 같은 컴퓨터(127.0.0.1, ::1)에서 온 요청에만 준다.
 * 주소는 헤더(조작 가능)가 아니라 실제 TCP 접속 주소(getRemoteAddr)로 판단한다(server.forward-headers-strategy=none).
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
        GatewayResult result = gateway.handle(request.prompt(), request.isConfirmed(), measure);

        // 측정 모드(개발용): 처리와 관계없이 200과 같은 형식으로 탐지 결과를 돌려준다.
        if (measure) {
            return ResponseEntity.ok(MeasurementResponse.from(result));
        }
        ChatResponse body = ChatResponse.from(result);
        return result.action() == Action.BLOCK
                ? ResponseEntity.status(HttpStatus.FORBIDDEN).body(body)
                : ResponseEntity.ok(body);
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
