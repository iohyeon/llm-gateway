package com.llmgateway.core.model

import com.llmgateway.core.decode.DecodePreset
import com.llmgateway.core.provider.ProviderId

/** 대화 참여자 역할. */
enum class Role { SYSTEM, USER, ASSISTANT }

/** 단일 대화 메시지. */
data class ChatMessage(
    val role: Role,
    val content: String,
)

/**
 * 공급자 비의존 완성 요청.
 * providerId 가 null 이면 게이트웨이 기본 공급자로 해석한다.
 * model 이 null 이면 어댑터 기본 모델을 사용한다.
 */
data class CompletionRequest(
    val messages: List<ChatMessage>,
    val preset: DecodePreset = DecodePreset.BALANCED,
    val maxOutputTokens: Int = 1024,
    val providerId: ProviderId? = null,
    val model: String? = null,
)
