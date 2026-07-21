package com.llmgateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * OpenAI 공급자 설정. application.yml 의 openai.* 로 바인딩한다.
 * apiKey 는 환경변수 OPENAI_API_KEY 로 주입한다. 레포에 값이 없어야 한다(BYO-key).
 */
@ConfigurationProperties(prefix = "openai")
data class OpenAiProperties(
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val baseUrl: String = "https://api.openai.com",
)
