package com.llmgateway.cache

import com.llmgateway.application.ResponseCache
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 인메모리 코사인 유사도 응답 캐시.
 * 임베딩이 L2 정규화돼 있으므로 코사인 = 내적. 임계값 이상 최고 유사 항목의 응답을 반환한다.
 *
 * 한계: 선형 스캔(O(n))이라 대규모엔 부적합. 실제 규모에선 벡터 인덱스(ANN)로 교체한다.
 */
class InMemoryCosineResponseCache(
    private val threshold: Double,
    private val maxEntries: Int,
) : ResponseCache {

    private data class Entry(val embedding: FloatArray, val response: String)

    private val entries = ConcurrentLinkedDeque<Entry>()

    override fun lookup(embedding: FloatArray): String? {
        var best: Entry? = null
        var bestSimilarity = -1.0
        for (entry in entries) {
            val similarity = dot(embedding, entry.embedding)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                best = entry
            }
        }
        return if (best != null && bestSimilarity >= threshold) best.response else null
    }

    override fun store(embedding: FloatArray, response: String) {
        while (entries.size >= maxEntries) {
            entries.pollFirst()
        }
        entries.addLast(Entry(embedding, response))
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return -1.0
        var sum = 0.0
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }
}
