package com.llmgateway.cache

import com.llmgateway.application.Embedder
import kotlin.math.sqrt

/**
 * 로컬 해싱 임베더(키 불필요). 토큰을 부호 있는 해싱으로 고정 차원 벡터에 눌러 담고 L2 정규화한다.
 *
 * 한계(정직): 어휘 겹침 기반이라 진짜 의미 유사도가 아니다. 근접 중복(같은/유사 프롬프트)에는
 * 잘 맞지만, 다른 단어의 패러프레이즈는 못 잡는다. 진짜 시맨틱 캐시가 필요하면
 * 임베딩 모델(OpenAI 등) 어댑터를 같은 Embedder 포트에 끼운다.
 */
class HashingEmbedder(
    private val dim: Int = 256,
) : Embedder {

    private val whitespace = Regex("\\s+")

    override fun embed(text: String): FloatArray {
        val vec = FloatArray(dim)
        text.lowercase().split(whitespace).filter { it.isNotBlank() }.forEach { token ->
            val h = token.hashCode()
            val index = Math.floorMod(h, dim)
            val sign = if ((h ushr 31) and 1 == 0) 1f else -1f
            vec[index] += sign
        }
        val norm = sqrt(vec.fold(0.0) { acc, v -> acc + v * v }).toFloat()
        if (norm > 0f) {
            for (i in vec.indices) vec[i] /= norm
        }
        return vec
    }
}
