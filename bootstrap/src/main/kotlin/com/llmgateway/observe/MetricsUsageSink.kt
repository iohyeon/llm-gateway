package com.llmgateway.observe

import com.llmgateway.application.UsageSink
import com.llmgateway.core.usage.UsageRecord
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import java.util.concurrent.TimeUnit

/**
 * 사용량을 Micrometer 메트릭으로 기록한다. provider 태그로 공급자별 집계.
 * /actuator/prometheus 로 노출된다.
 */
class MetricsUsageSink(
    private val registry: MeterRegistry,
) : UsageSink {

    override fun record(record: UsageRecord) {
        val tags = Tags.of(
            "provider", record.provider.name,
            "cache", if (record.cacheHit) "hit" else "miss",
        )
        registry.counter("llm.requests", tags).increment()
        registry.counter("llm.input.tokens", tags).increment(record.inputTokens.toDouble())
        registry.counter("llm.output.tokens", tags).increment(record.outputTokens.toDouble())
        registry.timer("llm.ttft", tags).record(record.ttftMillis, TimeUnit.MILLISECONDS)
        registry.timer("llm.tpot", tags).record(record.tpotMillis, TimeUnit.MILLISECONDS)
    }
}
