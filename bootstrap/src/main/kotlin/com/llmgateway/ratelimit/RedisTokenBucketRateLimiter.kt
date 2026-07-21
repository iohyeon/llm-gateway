package com.llmgateway.ratelimit

import com.llmgateway.application.RateLimiter
import com.llmgateway.application.RateLimiterBackendException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript

/**
 * Redis 기반 분산 토큰 버킷 레이트 리미터.
 *
 * 토큰 조회·리필·소비를 하나의 Lua 스크립트로 원자 실행한다. Redis 싱글스레드가
 * 스크립트를 통째로 실행하므로 read-modify-write 사이 경합이 없다.
 * (참고: "Lua Script가 Redis 원자성을 보장하는 진짜 이유".)
 *
 * Redis 장애 시 예외를 RateLimiterBackendException 으로 감싸 던진다.
 * 유스케이스가 Fail-Open/Fail-Closed 정책으로 처리한다.
 */
class RedisTokenBucketRateLimiter(
    private val redisTemplate: StringRedisTemplate,
    private val capacity: Long,
    private val refillPerSecond: Double,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val keyPrefix: String = "ratelimit:",
) : RateLimiter {

    private val script = RedisScript.of(LUA, Long::class.java)

    override fun tryAcquire(clientId: String): Boolean {
        return try {
            val allowed = redisTemplate.execute(
                script,
                listOf(keyPrefix + clientId),
                capacity.toString(),
                refillPerSecond.toString(),
                nowMillis().toString(),
                "1",
            )
            allowed == 1L
        } catch (e: Exception) {
            // Redis 다운·타임아웃 등. 정책이 결정하도록 위로 던진다.
            throw RateLimiterBackendException("redis rate limiter unavailable", e)
        }
    }

    private companion object {
        // KEYS[1]=버킷키, ARGV: capacity, refillPerSecond, nowMillis, requested
        val LUA = """
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(data[1])
            local ts = tonumber(data[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            local elapsed = math.max(0, now - ts) / 1000.0
            tokens = math.min(capacity, tokens + elapsed * refill)
            local allowed = 0
            if tokens >= requested then
              tokens = tokens - requested
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
            redis.call('PEXPIRE', KEYS[1], 60000)
            return allowed
        """.trimIndent()
    }
}
