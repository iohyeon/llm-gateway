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
)

/** 레이트 리밋 저장소 선택. */
enum class RateLimitBackend { MEMORY, REDIS }
