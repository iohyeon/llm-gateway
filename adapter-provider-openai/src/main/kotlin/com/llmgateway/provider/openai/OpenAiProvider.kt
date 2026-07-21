package com.llmgateway.provider.openai

import com.fasterxml.jackson.databind.ObjectMapper
import com.llmgateway.core.model.CompletionRequest
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
 * OpenAI Chat Completions API 스트리밍 어댑터.
 *
 * Anthropic 어댑터와의 대비(설계 R2):
 *  - 샘플링 파라미터: OpenAI 는 temperature 와 top_p 를 받는다. 따라서 DecodePreset 이
 *    실제 요청 파라미터로 번역된다. (Anthropic 현행 모델은 이를 거부해 생략했다.)
 *    단 top_k 는 OpenAI Chat Completions 미지원이라 번역에서 제외한다.
 *  - SYSTEM 역할: top-level 필드로 올리지 않고 role=system 메시지로 그대로 둔다.
 *    (Anthropic 은 top-level system 필드로 승격했다.)
 *  - 스트리밍: SSE data 의 choices[0].delta.content 를 토큰으로 방출. 종료 표식은 [DONE].
 */
class OpenAiProvider(
    private val webClient: WebClient,
    private val model: String,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : LlmProvider {

    override val id = ProviderId.OPENAI

    override fun complete(request: CompletionRequest): Flow<TokenChunk> {
        val flux = webClient.post()
            .uri("/v1/chat/completions")
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
        val preset = request.preset.params
        val messages = request.messages.map {
            mapOf("role" to it.role.name.lowercase(), "content" to it.content)
        }
        return buildMap {
            put("model", request.model ?: model)
            put("messages", messages)
            put("stream", true)
            put("max_tokens", request.maxOutputTokens)
            put("temperature", preset.temperature)
            put("top_p", preset.topP)
        }
    }

    /**
     * SSE 이벤트에서 토큰 텍스트를 추출한다.
     * [DONE] 은 종료 표식(방출 제외), error 오브젝트는 예외로 전파.
     */
    private fun extractText(event: ServerSentEvent<String>): String? {
        val data = event.data() ?: return null
        if (data == DONE_MARKER) return null

        val node = objectMapper.readTree(data)
        if (node.has("error")) {
            val message = node.path("error").path("message").asText("unknown error")
            throw OpenAiStreamException("OpenAI 스트림 오류: $message")
        }

        val content = node.path("choices").path(0).path("delta").path("content")
        return if (content.isTextual) content.asText() else null
    }

    private companion object {
        const val DONE_MARKER = "[DONE]"
        val SSE_TYPE = object : ParameterizedTypeReference<ServerSentEvent<String>>() {}
    }
}

class OpenAiStreamException(message: String) : RuntimeException(message)
