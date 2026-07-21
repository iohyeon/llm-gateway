package com.llmgateway.core.context

import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.Role
import com.llmgateway.core.token.TokenCount
import com.llmgateway.core.token.Tokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextBudgetTest {

    /** 메시지당 content 길이를 그대로 토큰 수로 세는 테스트용 토크나이저. */
    private val tokenizer = object : Tokenizer {
        override fun encode(text: String): List<Int> = text.map { it.code }
        override fun countMessages(messages: List<ChatMessage>): TokenCount =
            TokenCount(messages.sumOf { it.content.length })
    }

    @Test
    fun `윈도우에 들어오면 아무 메시지도 제거하지 않는다`() {
        val budget = ContextBudget(ContextWindow(maxTokens = 100), reserveForOutput = 10)
        val messages = listOf(
            ChatMessage(Role.USER, "a".repeat(20)),
            ChatMessage(Role.ASSISTANT, "b".repeat(20)),
        )

        val result = budget.fit(messages, tokenizer)

        assertEquals(0, result.truncatedCount)
        assertEquals(2, result.messages.size)
        assertEquals(40, result.inputTokens.value)
    }

    @Test
    fun `초과하면 오래된 비시스템 메시지부터 제거한다`() {
        val budget = ContextBudget(ContextWindow(maxTokens = 50), reserveForOutput = 10)
        val messages = listOf(
            ChatMessage(Role.USER, "a".repeat(30)),
            ChatMessage(Role.ASSISTANT, "b".repeat(30)),
            ChatMessage(Role.USER, "c".repeat(30)),
        )

        val result = budget.fit(messages, tokenizer)

        assertTrue(result.inputTokens.value <= 40)
        assertTrue(result.truncatedCount >= 2)
        assertEquals("c".repeat(30), result.messages.last().content)
    }

    @Test
    fun `시스템 메시지는 초과해도 유지한다`() {
        val budget = ContextBudget(ContextWindow(maxTokens = 20), reserveForOutput = 5)
        val messages = listOf(
            ChatMessage(Role.SYSTEM, "s".repeat(40)),
            ChatMessage(Role.USER, "u".repeat(40)),
        )

        val result = budget.fit(messages, tokenizer)

        assertTrue(result.messages.any { it.role == Role.SYSTEM })
        assertEquals(1, result.truncatedCount)
    }
}
