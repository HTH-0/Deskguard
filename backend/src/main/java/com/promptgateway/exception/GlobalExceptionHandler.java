package com.promptgateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * "이 프로젝트 전체에서 이런 종류의 예외가 발생하면 이렇게 응답해라"를 한 곳에 모아둔 클래스.
 * @RestControllerAdvice가 붙으면, 컨트롤러 안에서 try/catch를 안 해도
 * 여기 정의된 핸들러가 대신 잡아서 처리해준다. (컨트롤러 코드가 깔끔하게 유지되는 이유)
 *
 * 3단계 로드맵에서 요구한 "LLM API 호출 실패(타임아웃, 401, 429 등) 처리"가 여기 해당한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiErrorResponse("UPSTREAM_CLIENT_ERROR", ex.getMessage()));
    }

    // 연결 자체가 안 되거나(네트워크 문제), 응답이 타임아웃 시간 안에 안 온 경우
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleTimeout(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiErrorResponse("UPSTREAM_TIMEOUT",
                        "LLM API 응답이 시간 안에 오지 않았습니다."));
    }

    // 위에서 못 잡은 나머지 모든 예외 (예상 못한 버그 등) - 그래도 서버가 죽지 않고 JSON으로 응답하게 함
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnknown(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", ex.getMessage()));
    }
}
