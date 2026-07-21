package com.llmgateway.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SemanticCacheTest {

    private val embedder = HashingEmbedder()

    @Test
    fun `같은 프롬프트는 캐시 히트`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        val e = embedder.embed("오늘 날씨 어때")
        cache.store(e, "맑음")
        assertEquals("맑음", cache.lookup(embedder.embed("오늘 날씨 어때")))
    }

    @Test
    fun `단어가 겹치지 않으면 캐시 미스`() {
        val cache = InMemoryCosineResponseCache(threshold = 0.97, maxEntries = 100)
        cache.store(embedder.embed("오늘 날씨 어때"), "맑음")
        assertNull(cache.lookup(embedder.embed("환율 얼마야")))
    }

    @Test
    fun `임계값 미만 유사도는 미스`() {
        // 높은 임계값이면 부분 겹침도 미스가 된다.
        val cache = InMemoryCosineResponseCache(threshold = 0.99, maxEntries = 100)
        cache.store(embedder.embed("서울 오늘 날씨 어때 정말"), "맑음")
        assertNull(cache.lookup(embedder.embed("부산 내일 미세먼지 어때")))
    }
}
