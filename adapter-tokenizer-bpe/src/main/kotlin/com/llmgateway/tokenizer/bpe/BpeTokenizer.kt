package com.llmgateway.tokenizer.bpe

import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.token.TokenCount
import com.llmgateway.core.token.Tokenizer

/**
 * 학습된 BpeModel 로 인코딩/디코딩하는 토크나이저. Tokenizer 포트 구현.
 *
 * encode: 바이트 시퀀스에서 병합 순위(rank)가 가장 낮은(=먼저 학습된) 인접 쌍을
 * 반복 병합한다. 학습 노트 02의 "병합 규칙을 그대로 적용" 단계.
 *
 * 주의(설계 R1): 이 BPE 는 공급자 vocab 이 아니라 자체 학습 어휘다. 따라서 카운트는
 * 근사다. 공급자별 정확 카운트가 필요하면 해당 공급자 토크나이저·count-tokens 로 교체한다.
 */
class BpeTokenizer(
    private val model: BpeModel,
) : Tokenizer {

    override fun encode(text: String): List<Int> {
        val seq = text.encodeToByteArray().map { it.toInt() and 0xFF }.toMutableList()

        while (seq.size >= 2) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            for (i in 0 until seq.size - 1) {
                val rank = model.ranks[seq[i] to seq[i + 1]] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break

            val newId = model.newIdOf.getValue(seq[bestIndex] to seq[bestIndex + 1])
            seq[bestIndex] = newId
            seq.removeAt(bestIndex + 1)
        }
        return seq
    }

    /** 토큰 id 시퀀스를 원문으로 복원한다. round-trip 검증용. */
    fun decode(ids: List<Int>): String {
        val out = ArrayList<Byte>()
        for (id in ids) for (b in model.bytesFor(id)) out.add(b)
        return out.toByteArray().decodeToString()
    }

    override fun countMessages(messages: List<ChatMessage>): TokenCount {
        val total = messages.sumOf { encode(it.content).size + MESSAGE_OVERHEAD }
        return TokenCount(total.coerceAtLeast(messages.size))
    }

    private companion object {
        // 메시지당 역할·구분 오버헤드 근사.
        const val MESSAGE_OVERHEAD = 3
    }
}
