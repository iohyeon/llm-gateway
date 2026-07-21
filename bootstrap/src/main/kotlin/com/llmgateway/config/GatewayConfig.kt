package com.llmgateway.config

import com.llmgateway.application.ChatCompletionUseCase
import com.llmgateway.application.ProviderRegistry
import com.llmgateway.application.RateLimiter
import com.llmgateway.application.UsageSink
import com.llmgateway.core.context.ContextWindow
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.token.Tokenizer
import com.llmgateway.provider.anthropic.AnthropicProvider
import com.llmgateway.provider.gemini.GeminiProvider
import com.llmgateway.provider.openai.OpenAiProvider
import com.llmgateway.ratelimit.InMemoryTokenBucketRateLimiter
import com.llmgateway.ratelimit.RedisTokenBucketRateLimiter
import com.llmgateway.tokenizer.bpe.BpeTokenizerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.web.reactive.function.client.WebClient

/**
 * 조립 계층. 순수 도메인·유스케이스를 빈으로 연결한다.
 * core-domain·application 은 Spring 을 모른다. 여기서만 DI 로 조립한다.
 */
@Configuration
@EnableConfigurationProperties(
    GatewayProperties::class,
    AnthropicProperties::class,
    OpenAiProperties::class,
    GeminiProperties::class,
)
class GatewayConfig {

    // 번들 코퍼스로 학습한 byte-level BPE 토크나이저(직접 구현, 노트 02).
    @Bean
    fun tokenizer(): Tokenizer = BpeTokenizerFactory.default()

    /**
     * ANTHROPIC_API_KEY 가 설정된 경우에만 실제 공급자를 등록한다.
     * 미설정 시 fake 공급자만 남아 키 없이도 배선을 확인할 수 있다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "anthropic", name = ["api-key"], matchIfMissing = false)
    fun anthropicProvider(props: AnthropicProperties): LlmProvider {
        val webClient = WebClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("x-api-key", props.apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .build()
        return AnthropicProvider(webClient = webClient, model = props.model)
    }

    /**
     * OPENAI_API_KEY 가 설정된 경우에만 등록한다.
     * Anthropic 과 달리 DecodePreset 이 실제 temperature/top_p 로 번역된다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "openai", name = ["api-key"], matchIfMissing = false)
    fun openAiProvider(props: OpenAiProperties): LlmProvider {
        val webClient = WebClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("Authorization", "Bearer ${props.apiKey}")
            .build()
        return OpenAiProvider(webClient = webClient, model = props.model)
    }

    /** GEMINI_API_KEY 가 설정된 경우에만 등록한다. 세 공급자 중 가장 풍부한 프리셋 번역. */
    @Bean
    @ConditionalOnProperty(prefix = "gemini", name = ["api-key"], matchIfMissing = false)
    fun geminiProvider(props: GeminiProperties): LlmProvider {
        val webClient = WebClient.builder()
            .baseUrl(props.baseUrl)
            .defaultHeader("x-goog-api-key", props.apiKey)
            .build()
        return GeminiProvider(webClient = webClient, model = props.model)
    }

    /**
     * 레이트 리미터. 설정된 백엔드(memory|redis)로 선택한다.
     * redis 선택 시에도 Redis 연결은 지연 생성되며, 장애는 Fail-Open/Fail-Closed 정책이 처리한다.
     */
    @Bean
    fun rateLimiter(
        props: GatewayProperties,
        redisTemplate: ObjectProvider<StringRedisTemplate>,
    ): RateLimiter = when (props.rateLimitBackend) {
        RateLimitBackend.MEMORY -> InMemoryTokenBucketRateLimiter(
            capacity = props.rateLimitCapacity,
            refillPerSecond = props.rateLimitRefillPerSecond,
        )
        RateLimitBackend.REDIS -> RedisTokenBucketRateLimiter(
            redisTemplate = redisTemplate.getObject(),
            capacity = props.rateLimitCapacity,
            refillPerSecond = props.rateLimitRefillPerSecond,
        )
    }

    @Bean
    fun chatCompletionUseCase(
        tokenizer: Tokenizer,
        registry: ProviderRegistry,
        usageSink: UsageSink,
        rateLimiter: RateLimiter,
        props: GatewayProperties,
    ): ChatCompletionUseCase = ChatCompletionUseCase(
        tokenizer = tokenizer,
        registry = registry,
        window = ContextWindow(props.contextWindowTokens),
        reserveForOutput = props.reserveOutputTokens,
        defaultProvider = props.defaultProvider,
        usageSink = usageSink,
        rateLimiter = rateLimiter,
        backendFailurePolicy = props.rateLimitOnBackendError,
    )
}
