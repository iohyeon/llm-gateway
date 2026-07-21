package com.llmgateway.application

import com.llmgateway.core.context.BudgetResult
import com.llmgateway.core.context.ContextBudget
import com.llmgateway.core.context.ContextWindow
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import com.llmgateway.core.token.Tokenizer
import com.llmgateway.core.usage.UsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * 완성 요청 오케스트레이션.
 * 흐름: 레이트 리밋 → 컨텍스트 예산 → (시맨틱 캐시 조회) → 공급자 스트림 → 측정·저장.
 * 공급자·프레임워크에 의존하지 않는다.
 */
class ChatCompletionUseCase(
    private val tokenizer: Tokenizer,
    private val registry: ProviderRegistry,
    private val window: ContextWindow,
    private val reserveForOutput: Int,
    private val defaultProvider: ProviderId,
    private val usageSink: UsageSink,
    private val rateLimiter: RateLimiter,
    private val backendFailurePolicy: BackendFailurePolicy,
    private val embedder: Embedder,
    private val responseCache: ResponseCache,
    private val cacheEnabled: Boolean,
) {
    fun stream(request: CompletionRequest, clientId: String): Flow<TokenChunk> {
        enforceRateLimit(clientId)

        val budget = ContextBudget(window, reserveForOutput).fit(request.messages, tokenizer)
        val providerId = request.providerId ?: defaultProvider

        if (cacheEnabled) {
            val embedding = embedder.embed(promptTextOf(budget))
            val cached = responseCache.lookup(embedding)
            if (cached != null) {
                usageSink.record(
                    UsageRecord(
                        provider = providerId,
                        inputTokens = budget.inputTokens.value,
                        outputTokens = 0,
                        costEstimate = null,
                        ttftMillis = 0,
                        tpotMillis = 0,
                        cacheHit = true,
                    ),
                )
                return flowOf(TokenChunk(text = cached, index = 0))
            }
            return streamFromProvider(providerId, budget, request, embeddingToStore = embedding)
        }

        return streamFromProvider(providerId, budget, request, embeddingToStore = null)
    }

    private fun streamFromProvider(
        providerId: ProviderId,
        budget: BudgetResult,
        request: CompletionRequest,
        embeddingToStore: FloatArray?,
    ): Flow<TokenChunk> {
        val provider = registry.resolve(providerId)
        val effective = request.copy(messages = budget.messages)

        val startNanos = System.nanoTime()
        var firstTokenNanos = 0L
        var count = 0
        val collected = StringBuilder()

        return provider.complete(effective)
            .onEach { chunk ->
                if (count == 0) firstTokenNanos = System.nanoTime()
                count++
                if (embeddingToStore != null) collected.append(chunk.text)
            }
            .onCompletion { cause ->
                val ttft = millisSince(startNanos, firstTokenNanos.takeIf { it > 0 } ?: startNanos)
                val totalMillis = millisSince(startNanos, System.nanoTime())
                val tpot = if (count > 1) (totalMillis - ttft) / (count - 1) else 0L
                usageSink.record(
                    UsageRecord(
                        provider = providerId,
                        inputTokens = budget.inputTokens.value,
                        outputTokens = count,
                        costEstimate = null,
                        ttftMillis = ttft,
                        tpotMillis = tpot,
                        cacheHit = false,
                    ),
                )
                if (cause == null && embeddingToStore != null) {
                    responseCache.store(embeddingToStore, collected.toString())
                }
            }
    }

    private fun promptTextOf(budget: BudgetResult): String =
        budget.messages.joinToString("\n") { it.content }

    /**
     * 공급자 호출 전에 레이트 리밋을 강제한다.
     * 백엔드 장애 시 설정된 Fail-Open/Fail-Closed 정책을 적용한다.
     */
    private fun enforceRateLimit(clientId: String) {
        val allowed = try {
            rateLimiter.tryAcquire(clientId)
        } catch (e: RateLimiterBackendException) {
            when (backendFailurePolicy) {
                BackendFailurePolicy.FAIL_OPEN -> true
                BackendFailurePolicy.FAIL_CLOSED -> false
            }
        }
        if (!allowed) throw RateLimitExceededException(clientId)
    }

    private fun millisSince(from: Long, to: Long): Long = (to - from) / 1_000_000
}
