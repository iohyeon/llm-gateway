package com.llmgateway.provider

import com.llmgateway.application.UsageSink
import com.llmgateway.core.usage.UsageRecord
import org.slf4j.LoggerFactory

/** 사용량을 로그로 남기는 싱크. 조립 계층에서 composite 로 메트릭 싱크와 함께 묶인다. */
class LoggingUsageSink : UsageSink {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun record(record: UsageRecord) {
        log.info(
            "usage provider={} in={} out={} ttft={}ms tpot={}ms",
            record.provider,
            record.inputTokens,
            record.outputTokens,
            record.ttftMillis,
            record.tpotMillis,
        )
    }
}
