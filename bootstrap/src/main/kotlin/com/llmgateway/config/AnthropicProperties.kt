package com.llmgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Anthropic 공급자 설정. application.yml 의 anthropic.* 로 바인딩한다.
 * apiKey 는 환경변수 ANTHROPIC_API_KEY 로 주입한다. 레포에 값이 없어야 한다(BYO-key).
 */
@ConfigurationProperties(prefix = "anthropic")
data class AnthropicProperties(
    val apiKey: String = "",
    val model: String = "claude-opus-4-8",
    val baseUrl: String = "https://api.anthropic.com",
)
