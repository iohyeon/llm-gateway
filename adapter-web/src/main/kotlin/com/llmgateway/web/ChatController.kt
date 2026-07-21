package com.llmgateway.web

import com.llmgateway.application.ChatCompletionUseCase
import com.llmgateway.web.dto.ChatRequestDto
import com.llmgateway.web.dto.ChatResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 인바운드 어댑터. 모든 LLM 완성 요청의 진입점.
 * 스트리밍은 SSE 로, 비스트리밍은 집계 후 JSON 으로 응답한다.
 */
@RestController
@RequestMapping("/v1/chat")
class ChatController(
    private val useCase: ChatCompletionUseCase,
) {
    /** 토큰 단위 스트리밍. decode 루프의 각 토큰을 SSE 이벤트로 방출한다. */
    @PostMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @RequestBody body: ChatRequestDto,
        @RequestHeader(name = CLIENT_ID_HEADER, required = false) clientId: String?,
    ): Flow<ServerSentEvent<String>> =
        useCase.stream(body.toDomain(), clientId ?: ANONYMOUS)
            .map { chunk ->
                ServerSentEvent.builder(chunk.text)
                    .event("token")
                    .id(chunk.index.toString())
                    .build()
            }

    /** 비스트리밍. 전체 토큰을 모아 한 번에 반환한다. */
    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun complete(
        @RequestBody body: ChatRequestDto,
        @RequestHeader(name = CLIENT_ID_HEADER, required = false) clientId: String?,
    ): ChatResponseDto {
        val content = useCase.stream(body.toDomain(), clientId ?: ANONYMOUS)
            .map { it.text }
            .toList()
            .joinToString("")
        return ChatResponseDto(content)
    }

    private companion object {
        const val CLIENT_ID_HEADER = "X-Client-Id"
        const val ANONYMOUS = "anonymous"
    }
}
