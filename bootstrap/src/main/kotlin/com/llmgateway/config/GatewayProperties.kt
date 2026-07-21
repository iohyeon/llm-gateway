package com.llmgateway.config

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
)
