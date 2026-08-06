package com.llmgateway.config

import com.llmgateway.application.BackendFailurePolicy
import com.llmgateway.core.provider.ProviderId
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 게이트웨이 설정. application.yml 의 gateway.* 로 바인딩한다.
 * defaultProvider 는 환경변수 LLM_PROVIDER 로 주입한다.
 */
@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val defaultProvider: ProviderId = ProviderId.FAKE,
    val contextWindowTokens: Int = 200_000,
    val reserveOutputTokens: Int = 4_096,
    // 레이트 리밋: 백엔드(memory|redis), 클라이언트당 버킷 용량, 초당 리필, 백엔드 장애 정책.
    val rateLimitBackend: RateLimitBackend = RateLimitBackend.MEMORY,
    val rateLimitCapacity: Long = 20,
    val rateLimitRefillPerSecond: Double = 5.0,
    val rateLimitOnBackendError: BackendFailurePolicy = BackendFailurePolicy.FAIL_OPEN,
    // 시맨틱 캐시: 활성 여부, 유사도 임계값(1.0=동일), 최대 항목 수.
    val cacheEnabled: Boolean = false,
    val cacheSimilarityThreshold: Double = 0.97,
    val cacheMaxEntries: Int = 1_000,
    // 공급자 회복탄력성: 연결/응답/유휴 타임아웃과 일시적 오류 재시도. 아래 providerResilience() 로 조립.
    val providerConnectTimeoutMillis: Int = 2_000,
    val providerResponseTimeoutMillis: Long = 30_000,
    val providerReadIdleTimeoutMillis: Long = 60_000,
    val providerMaxRetries: Long = 2,
    val providerRetryBackoffMillis: Long = 200,
) {
    fun providerResilience(): ProviderResilience = ProviderResilience(
        connectTimeoutMillis = providerConnectTimeoutMillis,
        responseTimeoutMillis = providerResponseTimeoutMillis,
        readIdleTimeoutMillis = providerReadIdleTimeoutMillis,
        maxRetries = providerMaxRetries,
        retryBackoffMillis = providerRetryBackoffMillis,
    )
}

/**
 * 공급자 WebClient 회복탄력성 정책.
 * - connectTimeoutMillis: TCP 연결 수립 상한. 도달 불가 호스트에서의 무한 대기 차단.
 * - responseTimeoutMillis: 요청 전송 후 응답(헤더) 수신 상한. 응답을 시작조차 않는 업스트림 차단.
 *   응답 수신 시 해제되므로 정상적인 장문 스트림을 끊지 않는다.
 * - readIdleTimeoutMillis: 연결에서 읽기가 없는 최대 유휴 시간. 헤더 수신 후 스트림이
 *   중간에 멎는(hang) 경우를 차단한다. 토큰이 흐르는 동안에는 매 읽기마다 리셋되어 발화하지 않는다.
 * - maxRetries / retryBackoffMillis: 일시적 오류 한정 지수 백오프 재시도.
 */
data class ProviderResilience(
    val connectTimeoutMillis: Int,
    val responseTimeoutMillis: Long,
    val readIdleTimeoutMillis: Long,
    val maxRetries: Long,
    val retryBackoffMillis: Long,
)

/** 레이트 리밋 저장소 선택. */
enum class RateLimitBackend { MEMORY, REDIS }
