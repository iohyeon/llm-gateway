package com.llmgateway.core.provider

import com.llmgateway.core.model.CompletionRequest
import kotlinx.coroutines.flow.Flow

/** 지원 공급자 식별자. */
enum class ProviderId { ANTHROPIC, OPENAI, GEMINI, FAKE }

/** 스트리밍 토큰 조각. index 는 방출 순서. */
data class TokenChunk(
    val text: String,
    val index: Int,
)

/**
 * LLM 공급자 포트(아웃바운드).
 * 공급자별 요청 매핑과 SSE 파싱은 어댑터 내부에 둔다.
 * 반환 Flow 는 decode 루프의 토큰 방출을 그대로 표현한다. 학습 노트 09 참조.
 */
interface LlmProvider {
    val id: ProviderId

    fun complete(request: CompletionRequest): Flow<TokenChunk>
}
