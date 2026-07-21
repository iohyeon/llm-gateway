package com.llmgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Gemini 공급자 설정. application.yml 의 gemini.* 로 바인딩한다.
 * apiKey 는 환경변수 GEMINI_API_KEY 로 주입한다. 레포에 값이 없어야 한다(BYO-key).
 */
@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    val apiKey: String = "",
    val model: String = "gemini-2.0-flash",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
)
