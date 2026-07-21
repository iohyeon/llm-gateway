package com.llmgateway.core.token

import com.llmgateway.core.model.ChatMessage

/** 토큰 수. 비용·컨텍스트 예산의 기본 단위. */
@JvmInline
value class TokenCount(val value: Int) {
    operator fun plus(other: TokenCount) = TokenCount(value + other.value)
}

/**
 * 토크나이저 포트. 텍스트를 토큰으로 쪼개고 수를 센다.
 * 구현은 어댑터에 둔다. 학습 노트 02 (토크나이제이션) 참조.
 */
interface Tokenizer {
    /** 텍스트를 토큰 ID 시퀀스로 인코딩한다. */
    fun encode(text: String): List<Int>

    /** 메시지 목록의 총 토큰 수를 센다. 공급자 호출 전 컨텍스트 예산에 사용한다. */
    fun countMessages(messages: List<ChatMessage>): TokenCount
}
