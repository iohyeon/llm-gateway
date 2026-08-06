package com.llmgateway.application

import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.usage.CostEstimate
import com.llmgateway.core.usage.UsageRecord

/**
 * 공급자 식별자로 활성 공급자를 해석하는 포트. 구현은 조립 계층(bootstrap).
 * 요청한 공급자가 등록되지 않았으면 UnknownProviderException 을 던진다.
 */
interface ProviderRegistry {
    fun resolve(id: ProviderId): LlmProvider
}

/**
 * 요청한 공급자가 활성(등록) 목록에 없음을 알린다.
 * 원인은 클라이언트가 게이트웨이에 배선되지 않은 공급자를 지정한 것이므로
 * 서버 내부 오류(500)가 아니라 클라이언트 대상 오류다. 인바운드 어댑터가 404 로 매핑한다.
 */
class UnknownProviderException(
    val requested: ProviderId,
    val active: Set<ProviderId>,
) : RuntimeException("등록되지 않은 공급자: $requested (활성 공급자: $active)")

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
 * lookup: 같은 namespace 안에서 임계값 이상 유사한 캐시가 있으면 응답 텍스트, 없으면 null.
 * store: namespace·임베딩·응답을 저장.
 *
 * namespace 는 응답 형태를 좌우하는 파라미터(provider·model·preset·maxOutputTokens 등)를
 * 정규화한 격리 키다. 시맨틱 유사도(코사인)는 같은 namespace 안에서만 비교하므로,
 * 예컨대 OpenAI 요청이 Gemini/FAKE 가 채운 캐시를 히트하는 교차 오염이 발생하지 않는다.
 * 프롬프트 텍스트를 오염시키지 않아 임베딩 기하(코사인 유사도)의 의미도 그대로 보존된다.
 */
interface ResponseCache {
    fun lookup(namespace: String, embedding: FloatArray): String?
    fun store(namespace: String, embedding: FloatArray, response: String)
}

/**
 * 토큰 사용량을 비용으로 추정하는 포트. 단가표(공급자·모델별)는 어댑터에 둔다.
 * 미등록 공급자·모델이면 null.
 */
interface CostEstimator {
    fun estimate(provider: ProviderId, model: String?, inputTokens: Int, outputTokens: Int): CostEstimate?
}
