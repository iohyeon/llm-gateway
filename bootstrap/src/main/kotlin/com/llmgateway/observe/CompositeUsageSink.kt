package com.llmgateway.observe

import com.llmgateway.application.UsageSink
import com.llmgateway.core.usage.UsageRecord

/** 여러 UsageSink 에 사용량을 팬아웃한다(로깅 + 메트릭 등). */
class CompositeUsageSink(
    private val sinks: List<UsageSink>,
) : UsageSink {
    override fun record(record: UsageRecord) {
        sinks.forEach { it.record(record) }
    }
}
