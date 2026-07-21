package com.llmgateway.core.usage

import com.llmgateway.core.provider.ProviderId

/** 비용 추정치. 공급자별 단가 미확정 시 null 로 둔다. */
data class CostEstimate(
    val amount: Double,
    val currency: String,
)

/**
 * 호출 단위 사용량 기록.
 * ttftMillis: 첫 토큰까지 지연(prefill 국면).
 * tpotMillis: 토큰당 평균 생성 간격(decode 국면).
 * 학습 노트 07, 09 참조.
 */
data class UsageRecord(
    val provider: ProviderId,
    val inputTokens: Int,
    val outputTokens: Int,
    val costEstimate: CostEstimate?,
    val ttftMillis: Long,
    val tpotMillis: Long,
    val cacheHit: Boolean = false,
)
