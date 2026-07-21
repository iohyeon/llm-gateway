package com.llmgateway.web

import com.llmgateway.application.RateLimitExceededException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientResponseException

/** 도메인 예외를 HTTP 상태로 매핑한다. */
@RestControllerAdvice
class ApiExceptionHandler {

    /** 레이트 리밋 초과 → 429. */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(e: RateLimitExceededException): ResponseEntity<Map<String, String>> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(mapOf("error" to "rate_limited", "clientId" to e.clientId))

    /**
     * 공급자(업스트림) HTTP 오류를 502 로 매핑하고 실제 상태·바디를 노출한다.
     * 공급자 4xx/5xx 를 500 으로 삼키지 않고 원인을 그대로 보여준다.
     */
    @ExceptionHandler(WebClientResponseException::class)
    fun handleUpstream(e: WebClientResponseException): ResponseEntity<Map<String, Any>> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(
                mapOf(
                    "error" to "provider_error",
                    "upstreamStatus" to e.statusCode.value(),
                    "upstreamBody" to e.responseBodyAsString.take(1000),
                ),
            )
}
