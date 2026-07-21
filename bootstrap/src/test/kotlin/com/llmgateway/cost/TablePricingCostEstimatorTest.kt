package com.llmgateway.cost

import com.llmgateway.core.provider.ProviderId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TablePricingCostEstimatorTest {

    private val estimator = TablePricingCostEstimator(
        pricing = mapOf(
            ProviderId.ANTHROPIC to TablePricingCostEstimator.Pricing(
                inputPerMillion = 5.0,
                outputPerMillion = 25.0,
            ),
        ),
    )

    @Test
    fun `입력·출력 토큰을 단가로 곱해 비용을 낸다`() {
        // 1,000,000 in × $5 + 1,000,000 out × $25 = $30
        val cost = estimator.estimate(ProviderId.ANTHROPIC, "claude-opus-4-8", 1_000_000, 1_000_000)!!
        assertEquals(30.0, cost.amount, 1e-9)
        assertEquals("USD", cost.currency)
    }

    @Test
    fun `단가표에 없는 공급자는 null`() {
        assertNull(estimator.estimate(ProviderId.GEMINI, null, 100, 100))
    }
}
