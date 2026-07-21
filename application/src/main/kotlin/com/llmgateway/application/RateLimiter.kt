package com.llmgateway.application

/**
 * 레이트 리미터 포트. 클라이언트 단위로 요청 허용 여부를 판정한다.
 * 구현(인메모리 토큰 버킷, 향후 Redis 등)은 어댑터에 둔다.
 */
interface RateLimiter {
    /**
     * @return true 허용, false 한도 초과.
     * @throws RateLimiterBackendException 백엔드 저장소 장애 시(예: Redis 다운).
     */
    fun tryAcquire(clientId: String): Boolean
}

/** 레이트 리미터 백엔드 저장소 장애. Fail-Open/Fail-Closed 정책의 트리거. */
class RateLimiterBackendException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * 백엔드 장애 시 정책.
 * FAIL_OPEN: 통과시킨다(가용성 우선, 매출 보호).
 * FAIL_CLOSED: 차단한다(비용·한도 보호).
 * 어느 쪽이 옳은지는 도메인이 정한다. LLM 호출은 실제 비용이라 정책이 명시적이어야 한다.
 */
enum class BackendFailurePolicy { FAIL_OPEN, FAIL_CLOSED }

/** 한도 초과로 요청이 거부됐음을 알린다. 인바운드 어댑터가 429 로 매핑한다. */
class RateLimitExceededException(val clientId: String) :
    RuntimeException("rate limit exceeded: $clientId")
