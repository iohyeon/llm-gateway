package com.llmgateway.core.context

import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.Role
import com.llmgateway.core.token.TokenCount
import com.llmgateway.core.token.Tokenizer

/** 모델 컨텍스트 윈도우 크기(토큰). */
data class ContextWindow(val maxTokens: Int)

/**
 * 컨텍스트 예산 적용 결과.
 * truncatedCount: 윈도우를 맞추려 제거한 비시스템 메시지 수.
 */
data class BudgetResult(
    val messages: List<ChatMessage>,
    val inputTokens: TokenCount,
    val truncatedCount: Int,
)

/**
 * 컨텍스트 예산 도메인 서비스.
 * 입력 토큰 + 출력 예약 토큰이 윈도우를 넘으면 오래된 비시스템 메시지부터 제거한다.
 * 시스템 메시지는 항상 유지한다. 학습 노트 02, 09 참조.
 */
class ContextBudget(
    private val window: ContextWindow,
    private val reserveForOutput: Int,
) {
    fun fit(messages: List<ChatMessage>, tokenizer: Tokenizer): BudgetResult {
        val available = window.maxTokens - reserveForOutput
        val system = messages.filter { it.role == Role.SYSTEM }
        val rest = messages.filterNot { it.role == Role.SYSTEM }.toMutableList()

        var dropped = 0
        while (rest.isNotEmpty() &&
            tokenizer.countMessages(system + rest).value > available
        ) {
            rest.removeAt(0)
            dropped++
        }

        val kept = system + rest
        return BudgetResult(
            messages = kept,
            inputTokens = tokenizer.countMessages(kept),
            truncatedCount = dropped,
        )
    }
}
