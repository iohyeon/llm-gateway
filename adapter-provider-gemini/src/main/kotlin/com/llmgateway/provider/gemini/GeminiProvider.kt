package com.llmgateway.provider.gemini

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
 * Google Gemini generateContent 스트리밍 어댑터.
 *
 * 세 번째 공급자 대비(설계 R2). 가장 풍부한 번역이다.
 *  - 샘플링: temperature, top_p 에 더해 top_k 까지 받는다.
 *    (Anthropic: 셋 다 생략, OpenAI: top_k 미지원. Gemini 는 셋 다 지원.)
 *  - SYSTEM 역할: top-level `systemInstruction` 필드로 (Anthropic 의 system 승격과 유사).
 *  - assistant 역할: Gemini 는 `model` 로 부른다. (다른 둘은 assistant.)
 *  - 메시지 구조: `contents[].parts[].text`.
 *  - 스트리밍: ?alt=sse, data 의 candidates[0].content.parts[0].text.
 */
class GeminiProvider(
    private val webClient: WebClient,
    private val model: String,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : LlmProvider {

    override val id = ProviderId.GEMINI

    override fun complete(request: CompletionRequest): Flow<TokenChunk> {
        val modelName = request.model ?: model
        val flux = webClient.post()
            .uri("/v1beta/models/{model}:streamGenerateContent?alt=sse", modelName)
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

        val contents = request.messages
            .filterNot { it.role == Role.SYSTEM }
            .map { message ->
                val geminiRole = if (message.role == Role.ASSISTANT) "model" else "user"
                mapOf("role" to geminiRole, "parts" to listOf(mapOf("text" to message.content)))
            }

        val systemText = request.messages
            .filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.content }

        val generationConfig = buildMap {
            put("temperature", preset.temperature)
            put("topP", preset.topP)
            preset.topK?.let { put("topK", it) } // Gemini 는 top_k 를 받는다
            put("maxOutputTokens", request.maxOutputTokens)
        }

        return buildMap {
            put("contents", contents)
            put("generationConfig", generationConfig)
            if (systemText.isNotBlank()) {
                put("systemInstruction", mapOf("parts" to listOf(mapOf("text" to systemText))))
            }
        }
    }

    private fun extractText(event: ServerSentEvent<String>): String? {
        val data = event.data() ?: return null
        val node = objectMapper.readTree(data)
        if (node.has("error")) {
            val message = node.path("error").path("message").asText("unknown error")
            throw GeminiStreamException("Gemini 스트림 오류: $message")
        }
        val text = node.path("candidates").path(0).path("content").path("parts").path(0).path("text")
        return if (text.isTextual) text.asText() else null
    }

    private companion object {
        val SSE_TYPE = object : ParameterizedTypeReference<ServerSentEvent<String>>() {}
    }
}

class GeminiStreamException(message: String) : RuntimeException(message)
