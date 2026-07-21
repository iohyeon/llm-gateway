package com.llmgateway.ratelimit

import com.llmgateway.application.RateLimiter
import java.util.concurrent.ConcurrentHashMap

/**
 * 인메모리 토큰 버킷 레이트 리미터. 클라이언트별 버킷을 유지한다.
 *
 * 한계(정직): 프로세스 로컬이라 분산 환경에서 인스턴스가 여러 대면 한도가 대수만큼 커진다.
 * 분산 정확성이 필요하면 Redis 어댑터로 교체한다(그때 Fail-Open/Fail-Closed 정책이 실제로 동작).
 * 인메모리 경로에는 백엔드 장애가 없어 정책은 휴면 상태다.
 */
class InMemoryTokenBucketRateLimiter(
    private val capacity: Long,
    private val refillPerSecond: Double,
) : RateLimiter {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun tryAcquire(clientId: String): Boolean =
        buckets.computeIfAbsent(clientId) { Bucket(capacity, refillPerSecond) }.tryConsume()

    private class Bucket(
        private val capacity: Long,
        private val refillPerSecond: Double,
    ) {
        private var tokens: Double = capacity.toDouble()
        private var lastRefillNanos: Long = System.nanoTime()

        @Synchronized
        fun tryConsume(): Boolean {
            refill()
            return if (tokens >= 1.0) {
                tokens -= 1.0
                true
            } else {
                false
            }
        }

        private fun refill() {
            val now = System.nanoTime()
            val elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0
            if (elapsedSeconds > 0) {
                tokens = (tokens + elapsedSeconds * refillPerSecond).coerceAtMost(capacity.toDouble())
                lastRefillNanos = now
            }
        }
    }
}
