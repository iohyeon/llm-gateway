package com.llmgateway.provider

import com.llmgateway.application.UsageSink
import com.llmgateway.core.usage.UsageRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 사용량을 로그로 남기는 싱크. 추후 메트릭·저장 싱크로 확장한다. */
@Component
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
