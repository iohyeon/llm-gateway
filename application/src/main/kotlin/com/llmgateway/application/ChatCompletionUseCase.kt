package com.llmgateway.application

import com.llmgateway.core.context.ContextBudget
import com.llmgateway.core.context.ContextWindow
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import com.llmgateway.core.token.Tokenizer
import com.llmgateway.core.usage.UsageRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

/**
 * 완성 요청 오케스트레이션.
 * 흐름: 컨텍스트 예산 적용 → 공급자 해석 → 스트림 → TTFT/TPOT 측정 → 사용량 기록.
 * 공급자·프레임워크에 의존하지 않는다.
 */
class ChatCompletionUseCase(
    private val tokenizer: Tokenizer,
    private val registry: ProviderRegistry,
    private val window: ContextWindow,
    private val reserveForOutput: Int,
    private val defaultProvider: ProviderId,
    private val usageSink: UsageSink,
) {
    fun stream(request: CompletionRequest): Flow<TokenChunk> {
        val budget = ContextBudget(window, reserveForOutput).fit(request.messages, tokenizer)
        val providerId = request.providerId ?: defaultProvider
        val provider = registry.resolve(providerId)
        val effective = request.copy(messages = budget.messages)

        val startNanos = System.nanoTime()
        var firstTokenNanos = 0L
        var count = 0

        return provider.complete(effective)
            .onEach {
                if (count == 0) firstTokenNanos = System.nanoTime()
                count++
            }
            .onCompletion {
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
                    ),
                )
            }
    }

    private fun millisSince(from: Long, to: Long): Long = (to - from) / 1_000_000
}
