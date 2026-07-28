package com.llmgateway.application

import com.llmgateway.core.context.ContextWindow
import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.model.Role
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import com.llmgateway.core.token.TokenCount
import com.llmgateway.core.token.Tokenizer
import com.llmgateway.core.usage.UsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RateLimitPolicyTest {

    private val tokenizer = object : Tokenizer {
        override fun encode(text: String): List<Int> = emptyList()
        override fun countMessages(messages: List<ChatMessage>) = TokenCount(1)
    }

    private val registry = object : ProviderRegistry {
        override fun resolve(id: ProviderId): LlmProvider = object : LlmProvider {
            override val id = ProviderId.FAKE
            override fun complete(request: CompletionRequest): Flow<TokenChunk> =
                flowOf(TokenChunk("hi", 0))
        }
    }

    private val sink = object : UsageSink {
        override fun record(record: UsageRecord) {}
    }

    private val request = CompletionRequest(messages = listOf(ChatMessage(Role.USER, "hi")))

    private val noOpEmbedder = object : Embedder {
        override fun embed(text: String) = FloatArray(4)
    }

    private val noOpCache = object : ResponseCache {
        override fun lookup(namespace: String, embedding: FloatArray): String? = null
        override fun store(namespace: String, embedding: FloatArray, response: String) {}
    }

    private val noOpCostEstimator = object : CostEstimator {
        override fun estimate(
            provider: ProviderId,
            model: String?,
            inputTokens: Int,
            outputTokens: Int,
        ) = null
    }

    private fun useCase(limiter: RateLimiter, policy: BackendFailurePolicy) = ChatCompletionUseCase(
        tokenizer = tokenizer,
        registry = registry,
        window = ContextWindow(1_000),
        reserveForOutput = 100,
        defaultProvider = ProviderId.FAKE,
        usageSink = sink,
        rateLimiter = limiter,
        backendFailurePolicy = policy,
        embedder = noOpEmbedder,
        responseCache = noOpCache,
        cacheEnabled = false,
        costEstimator = noOpCostEstimator,
    )

    private fun limiter(allow: Boolean) = object : RateLimiter {
        override fun tryAcquire(clientId: String) = allow
    }

    private val backendDown = object : RateLimiter {
        override fun tryAcquire(clientId: String): Boolean =
            throw RateLimiterBackendException("down")
    }

    @Test
    fun `한도 초과면 RateLimitExceededException 을 던진다`() {
        assertFailsWith<RateLimitExceededException> {
            useCase(limiter(allow = false), BackendFailurePolicy.FAIL_OPEN).stream(request, "c1")
        }
    }

    @Test
    fun `백엔드 장애 + FAIL_OPEN 이면 통과시킨다`() {
        // 예외 없이 스트림을 반환해야 한다(게이트가 열림).
        assertNotNull(useCase(backendDown, BackendFailurePolicy.FAIL_OPEN).stream(request, "c1"))
    }

    @Test
    fun `백엔드 장애 + FAIL_CLOSED 이면 차단한다`() {
        assertFailsWith<RateLimitExceededException> {
            useCase(backendDown, BackendFailurePolicy.FAIL_CLOSED).stream(request, "c1")
        }
    }
}
