package com.llmgateway.application

import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.usage.UsageRecord

/** 공급자 식별자로 활성 공급자를 해석하는 포트. 구현은 조립 계층(bootstrap). */
interface ProviderRegistry {
    fun resolve(id: ProviderId): LlmProvider
}

/** 호출 단위 사용량 기록 싱크. 로깅·메트릭·저장 등 구현은 어댑터. */
interface UsageSink {
    fun record(record: UsageRecord)
}

/**
 * 텍스트를 벡터로 임베딩하는 포트. 시맨틱 캐시의 유사도 계산에 쓴다.
 * 기본 구현은 로컬 해싱 임베더(키 불필요). 실제 의미 유사도가 필요하면
 * 임베딩 모델(OpenAI 등) 어댑터로 교체한다. 학습 노트 03 참조.
 */
interface Embedder {
    fun embed(text: String): FloatArray
}

/**
 * 임베딩 유사도 기반 응답 캐시 포트.
 * lookup: 임계값 이상 유사한 캐시가 있으면 응답 텍스트, 없으면 null.
 * store: 임베딩과 응답을 저장.
 */
interface ResponseCache {
    fun lookup(embedding: FloatArray): String?
    fun store(embedding: FloatArray, response: String)
}
