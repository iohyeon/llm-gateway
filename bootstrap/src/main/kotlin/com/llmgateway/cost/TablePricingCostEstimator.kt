package com.llmgateway.cost

import com.llmgateway.application.CostEstimator
import com.llmgateway.core.provider.ProviderId
import com.llmgateway.core.usage.CostEstimate

/**
 * 단가표 기반 비용 추정기. 비용 = 입력토큰/1e6 × 입력단가 + 출력토큰/1e6 × 출력단가.
 *
 * 단가는 예시 기본값이다(공급자·모델별 실제 단가는 수시로 바뀌므로 운영에서 갱신·설정 주입 필요).
 * FAKE 는 데모용 명목 단가라 키 없이도 비용 메트릭이 0이 아니게 찍힌다.
 */
class TablePricingCostEstimator(
    private val pricing: Map<ProviderId, Pricing> = DEFAULT,
    private val currency: String = "USD",
) : CostEstimator {

    /** 100만 토큰당 단가(USD). */
    data class Pricing(val inputPerMillion: Double, val outputPerMillion: Double)

    override fun estimate(
        provider: ProviderId,
        model: String?,
        inputTokens: Int,
        outputTokens: Int,
    ): CostEstimate? {
        val p = pricing[provider] ?: return null
        val amount = inputTokens / 1_000_000.0 * p.inputPerMillion +
            outputTokens / 1_000_000.0 * p.outputPerMillion
        return CostEstimate(amount = amount, currency = currency)
    }

    companion object {
        // 예시 기본 단가(USD / 1M tokens). 실제 값은 갱신 필요.
        val DEFAULT: Map<ProviderId, Pricing> = mapOf(
            ProviderId.ANTHROPIC to Pricing(inputPerMillion = 5.0, outputPerMillion = 25.0),
            ProviderId.OPENAI to Pricing(inputPerMillion = 0.15, outputPerMillion = 0.60),
            ProviderId.GEMINI to Pricing(inputPerMillion = 0.10, outputPerMillion = 0.40),
            ProviderId.FAKE to Pricing(inputPerMillion = 1.0, outputPerMillion = 2.0),
        )
    }
}
