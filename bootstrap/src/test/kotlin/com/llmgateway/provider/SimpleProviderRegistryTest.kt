package com.llmgateway.provider

import com.llmgateway.application.UnknownProviderException
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleProviderRegistryTest {

    private class StubProvider(override val id: ProviderId) : LlmProvider {
        override fun complete(request: CompletionRequest): Flow<TokenChunk> = emptyFlow()
    }

    @Test
    fun `등록된 공급자는 해석한다`() {
        val registry = SimpleProviderRegistry(listOf(StubProvider(ProviderId.FAKE)))
        assertEquals(ProviderId.FAKE, registry.resolve(ProviderId.FAKE).id)
    }

    @Test
    fun `미등록 공급자는 UnknownProviderException 을 던진다`() {
        // 500(IllegalArgumentException 미매핑)이 아니라 404 로 매핑 가능한 도메인 예외여야 한다.
        val registry = SimpleProviderRegistry(listOf(StubProvider(ProviderId.FAKE)))
        val ex = assertThrows(UnknownProviderException::class.java) {
            registry.resolve(ProviderId.OPENAI)
        }
        assertEquals(ProviderId.OPENAI, ex.requested)
        assertTrue(ex.active.contains(ProviderId.FAKE))
    }
}
