package com.llmgateway.provider.anthropic

import com.fasterxml.jackson.databind.ObjectMapper
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.model.Role
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.provider.TokenChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.reactive.asFlow
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.reactive.function.client.WebClient

/**
 * Anthropic Messages API 스트리밍 어댑터.
 *
 * 공급자별 차이를 흡수하는 지점(설계 R2):
 *  - SYSTEM 역할 메시지는 messages 가 아니라 top-level system 필드로 올린다.
 *  - 샘플링 파라미터(temperature/top_p/top_k)는 보내지 않는다.
 *    현행 모델(Opus 4.8/4.7, Sonnet 5)은 이 값들을 거부(400)하고,
 *    그 이전 모델도 temperature 와 top_p 동시 전송은 400 이다.
 *    따라서 DecodePreset 은 Anthropic 경로에서 프롬프트 레벨 의도로만 남는다.
 *  - 스트리밍 SSE 는 content_block_delta 의 text_delta 만 토큰으로 방출한다.
 */
class AnthropicProvider(
    private val webClient: WebClient,
    private val model: String,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : LlmProvider {

    override val id = ProviderId.ANTHROPIC

    override fun complete(request: CompletionRequest): Flow<TokenChunk> {
        val flux = webClient.post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(buildBody(request))
            .retrieve()
            .bodyToFlux(SSE_TYPE)

        return flux.asFlow()
            .mapNotNull { event -> extractText(event) }
            .withIndex()
            .map { (index, text) -> TokenChunk(text = text, index = index) }
    }

    private fun buildBody(request: CompletionRequest): Map<String, Any?> {
        val system = request.messages
            .filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }

        val messages = request.messages
            .filterNot { it.role == Role.SYSTEM }
            .map { mapOf("role" to it.role.name.lowercase(), "content" to it.content) }

        return buildMap {
            put("model", request.model ?: model)
            put("max_tokens", request.maxOutputTokens)
            put("stream", true)
            put("messages", messages)
            if (system.isNotBlank()) put("system", system)
        }
    }

    /**
     * SSE 이벤트에서 토큰 텍스트를 추출한다. text_delta 가 아니면 null(방출 제외).
     * error 이벤트는 예외로 전파한다.
     */
    private fun extractText(event: ServerSentEvent<String>): String? {
        val data = event.data() ?: return null
        val node = objectMapper.readTree(data)

        when (node.path("type").asText()) {
            "content_block_delta" -> {
                val delta = node.path("delta")
                if (delta.path("type").asText() == "text_delta") {
                    return delta.path("text").asText()
                }
            }
            "error" -> {
                val message = node.path("error").path("message").asText("unknown error")
                throw AnthropicStreamException("Anthropic 스트림 오류: $message")
            }
        }
        return null
    }

    private companion object {
        val SSE_TYPE = object : ParameterizedTypeReference<ServerSentEvent<String>>() {}
    }
}

class AnthropicStreamException(message: String) : RuntimeException(message)
