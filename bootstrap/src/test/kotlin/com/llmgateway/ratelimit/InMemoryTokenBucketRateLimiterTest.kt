package com.llmgateway.ratelimit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryTokenBucketRateLimiterTest {

    @Test
    fun `용량까지 허용하고 초과하면 거부한다`() {
        val limiter = InMemoryTokenBucketRateLimiter(capacity = 3, refillPerSecond = 0.0)
        assertTrue(limiter.tryAcquire("c1"))
        assertTrue(limiter.tryAcquire("c1"))
        assertTrue(limiter.tryAcquire("c1"))
        assertFalse(limiter.tryAcquire("c1")) // 4번째는 거부
    }

    @Test
    fun `클라이언트별 버킷은 독립이다`() {
        val limiter = InMemoryTokenBucketRateLimiter(capacity = 1, refillPerSecond = 0.0)
        assertTrue(limiter.tryAcquire("a"))
        assertFalse(limiter.tryAcquire("a"))
        assertTrue(limiter.tryAcquire("b")) // 다른 클라이언트는 별도 버킷
    }
}
