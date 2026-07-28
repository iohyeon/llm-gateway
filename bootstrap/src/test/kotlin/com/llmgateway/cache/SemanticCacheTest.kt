package com.llmgateway.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SemanticCacheTest {

    private val embedder = HashingEmbedder()

    // 응답 형태를 좌우하는 파라미터를 정규화한 격리 키. 유스케이스의 cacheNamespaceOf 와 같은 규약.
    private fun ns(provider: String, model: String = "", preset: String = "BALANCED", maxOutputTokens: Int = 1024) =
        listOf(provider, model, preset, maxOutputTokens.toString()).joinToString("|")

    @Test
    fun `같은 프롬프트는 캐시 히트`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        val e = embedder.embed("오늘 날씨 어때")
        cache.store(ns("OPENAI"), e, "맑음")
        assertEquals("맑음", cache.lookup(ns("OPENAI"), embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `단어가 겹치지 않으면 캐시 미스`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(ns("OPENAI"), embedder.embed("오늘 날씨 어때"), "맑음")
        assertNull(cache.lookup(ns("OPENAI"), embedder.embed("환율 얼마야")))
    }

    @Test
    fun `임계값 미만 유사도는 미스`() {
        // 높은 임계값이면 부분 겹침도 미스가 된다.
        val cache = InMemoryCosineResponseCache(threshold = 0.99, maxEntries = 100)
        cache.store(ns("OPENAI"), embedder.embed("서울 오늘 날씨 어때 정말"), "맑음")
        assertNull(cache.lookup(ns("OPENAI"), embedder.embed("부산 내일 미세먼지 어때")))
    }

    @Test
    fun `프롬프트가 같아도 provider 가 다르면 캐시 미스`() {
        // 교차 오염 회귀: GEMINI 가 채운 캐시를 OPENAI 요청이 히트하면 안 된다.
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(ns("GEMINI"), embedder.embed("오늘 날씨 어때"), "제미니 응답")
        assertNull(cache.lookup(ns("OPENAI"), embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `프롬프트가 같아도 model 이 다르면 캐시 미스`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(ns("OPENAI", model = "gpt-4o"), embedder.embed("오늘 날씨 어때"), "4o 응답")
        assertNull(cache.lookup(ns("OPENAI", model = "gpt-4o-mini"), embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `프롬프트가 같아도 preset 이 다르면 캐시 미스`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(ns("OPENAI", preset = "FACTUAL_QA"), embedder.embed("오늘 날씨 어때"), "결정론 응답")
        assertNull(cache.lookup(ns("OPENAI", preset = "CREATIVE"), embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `프롬프트가 같아도 maxOutputTokens 가 다르면 캐시 미스`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(ns("OPENAI", maxOutputTokens = 256), embedder.embed("오늘 날씨 어때"), "짧은 응답")
        assertNull(cache.lookup(ns("OPENAI", maxOutputTokens = 4096), embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `같은 provider·model 이면 유사 프롬프트는 시맨틱 히트`() {
        // 격리는 유지하되 같은 namespace 안에서는 시맨틱 유사 매칭이 살아 있어야 한다.
        val cache = InMemoryCosineResponseCache(threshold = 0.5, maxEntries = 100)
        cache.store(ns("OPENAI"), embedder.embed("서울 오늘 날씨 어때"), "맑음")
        // 어휘가 상당 부분 겹치는 근접 프롬프트는 같은 namespace 에서 히트한다.
        assertEquals("맑음", cache.lookup(ns("OPENAI"), embedder.embed("서울 오늘 날씨")))
    }
}
