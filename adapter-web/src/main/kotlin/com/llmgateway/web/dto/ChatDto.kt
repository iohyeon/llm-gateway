package com.llmgateway.web.dto

import com.llmgateway.core.decode.DecodePreset
import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.model.Role
import com.llmgateway.core.provider.ProviderId

data class ChatMessageDto(
    val role: Role,
    val content: String,
) {
    fun toDomain() = ChatMessage(role, content)
}

data class ChatRequestDto(
    val messages: List<ChatMessageDto>,
    val preset: DecodePreset = DecodePreset.BALANCED,
    val maxOutputTokens: Int = 1024,
    val provider: ProviderId? = null,
    val model: String? = null,
) {
    fun toDomain() = CompletionRequest(
        messages = messages.map { it.toDomain() },
        preset = preset,
        maxOutputTokens = maxOutputTokens,
        providerId = provider,
        model = model,
    )
}

data class ChatResponseDto(
    val content: String,
)
