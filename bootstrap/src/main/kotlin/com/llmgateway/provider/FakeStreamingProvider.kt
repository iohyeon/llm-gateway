package com.llmgateway.provider

import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Component

/**
 * 키 없이 스트리밍 배선을 검증하는 로컬 더미 공급자.
 * 실제 Anthropic 어댑터(adapter-provider-anthropic)로 교체·병행된다.
 * decode 루프를 흉내내 토큰을 간격을 두고 방출한다.
 */
@Component
class FakeStreamingProvider : LlmProvider {

    override val id = ProviderId.FAKE

    override fun complete(request: CompletionRequest): Flow<TokenChunk> = flow {
        val reply = "안녕하세요. fake 공급자의 스트리밍 응답입니다. " +
            "여기 자리에 실제 Anthropic 어댑터가 들어갑니다. " +
            "preset=${request.preset} maxOut=${request.maxOutputTokens}"
        reply.split(" ").forEachIndexed { index, word ->
            delay(TOKEN_INTERVAL_MILLIS)
            emit(TokenChunk(text = "$word ", index = index))
        }
    }

    private companion object {
        const val TOKEN_INTERVAL_MILLIS = 40L
    }
}
