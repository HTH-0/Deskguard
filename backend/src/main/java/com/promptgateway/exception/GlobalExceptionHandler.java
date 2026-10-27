package com.promptgateway.exception;

import com.promptgateway.gateway.InvalidPromptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * "이 프로젝트 전체에서 이런 종류의 예외가 발생하면 이렇게 응답해라"를 한 곳에 모아둔 클래스.
 * @RestControllerAdvice가 붙으면, 컨트롤러 안에서 try/catch를 안 해도
 * 여기 정의된 핸들러가 대신 잡아서 처리해준다. (컨트롤러 코드가 깔끔하게 유지되는 이유)
 *
 * 사용자에게 돌려주는 message는 모두 고정 문구이다. 외부 응답 본문, 요청 주소, 클래스 이름 같은
 * 내부 정보는 응답에 싣지 않고 서버 로그에만 남긴다(설계 결정 D12).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 입력 검증 실패: 빈 문자열, 공백뿐, 최대 길이 초과, prompt 없음 (메시지는 InputValidator가 정한 안전한 문구)
    @ExceptionHandler(InvalidPromptException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPrompt(InvalidPromptException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_PROMPT", ex.getMessage()));
    }

    // 요청 본문이 JSON이 아니거나 읽을 수 없는 경우. 클라이언트 잘못이므로 500이 아니라 400이다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다. JSON 형식을 확인하세요."));
    }

    // Gemini가 401을 주는 경우 - API 키가 잘못됐거나 만료된 경우
    @ExceptionHandler(HttpClientErrorException.Unauthorized.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(HttpClientErrorException.Unauthorized ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("UPSTREAM_UNAUTHORIZED",
                        "LLM API 인증에 실패했습니다. API 키 설정을 확인하세요."));
    }

    // Gemini가 429를 주는 경우 - 무료 티어 요청 한도를 초과한 경우
    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(HttpClientErrorException.TooManyRequests ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ApiErrorResponse("UPSTREAM_RATE_LIMITED",
                        "LLM API 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요."));
    }

    // 그 외 4xx 응답 전체를 잡는 catch-all (위 두 개보다 더 일반적인 예외라 마지막에 둠)
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiErrorResponse> handleClientError(HttpClientErrorException ex) {
        log.warn("응답 모델이 요청을 거부했습니다: {}", ex.getStatusCode());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiErrorResponse("UPSTREAM_CLIENT_ERROR", "응답 모델이 요청을 처리하지 못했습니다."));
    }

    // 연결 자체가 안 되거나(네트워크 문제), 응답이 타임아웃 시간 안에 안 온 경우
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleTimeout(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiErrorResponse("UPSTREAM_TIMEOUT",
                        "LLM API 응답이 시간 안에 오지 않았습니다."));
    }

    // 위에서 못 잡은 나머지 모든 예외 (예상 못한 버그 등) - 그래도 서버가 죽지 않고 JSON으로 응답하게 함.
    // 자세한 내용은 사용자에게 보내지 않고 로그에만 남긴다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex) {
        log.error("처리하지 못한 오류가 발생했습니다: {}", ex.getClass().getSimpleName(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
