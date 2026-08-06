package com.llmgateway.provider

import com.llmgateway.application.ProviderRegistry
import com.llmgateway.application.UnknownProviderException
import com.llmgateway.core.provider.LlmProvider
import com.llmgateway.core.provider.ProviderId
import org.springframework.stereotype.Component

/**
 * 등록된 LlmProvider 빈들을 id 로 색인해 해석한다.
 * 새 공급자 어댑터를 빈으로 추가하면 자동 등록된다.
 */
@Component
class SimpleProviderRegistry(
    providers: List<LlmProvider>,
) : ProviderRegistry {

    private val byId: Map<ProviderId, LlmProvider> = providers.associateBy { it.id }

    override fun resolve(id: ProviderId): LlmProvider =
        byId[id] ?: throw UnknownProviderException(requested = id, active = byId.keys)
}
