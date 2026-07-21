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
