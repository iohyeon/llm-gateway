package com.llmgateway.web

import com.llmgateway.application.RateLimitExceededException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 도메인 예외를 HTTP 상태로 매핑한다. */
@RestControllerAdvice
class ApiExceptionHandler {

    /** 레이트 리밋 초과 → 429. */
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(e: RateLimitExceededException): ResponseEntity<Map<String, String>> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .body(mapOf("error" to "rate_limited", "clientId" to e.clientId))
}
