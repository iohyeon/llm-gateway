package com.llmgateway.tokenizer.bpe

/**
 * 학습된 BPE 모델. byte-level 이라 OOV 가 없다(최악의 경우 바이트 단위까지 쪼갬).
 * 학습 노트 02(토크나이제이션) 참조.
 *
 * - ranks: 병합 쌍 -> 병합 순서(작을수록 먼저 병합). encode 시 greedy 선택 기준.
 * - newIdOf: 병합 쌍 -> 새로 부여된 토큰 id.
 * - mergedBytes: 병합 토큰 id -> 복원 바이트열(decode 용). 기저 토큰(0..255)은 즉석 계산.
 */
class BpeModel(
    val ranks: Map<Pair<Int, Int>, Int>,
    val newIdOf: Map<Pair<Int, Int>, Int>,
    private val mergedBytes: Map<Int, ByteArray>,
) {
    val vocabSize: Int = BASE_VOCAB + mergedBytes.size

    fun bytesFor(id: Int): ByteArray =
        if (id < BASE_VOCAB) byteArrayOf(id.toByte()) else mergedBytes.getValue(id)

    companion object {
        const val BASE_VOCAB = 256
    }
}

/**
 * BPE 학습기. 코퍼스에서 가장 자주 붙어 나오는 인접 쌍을 반복 병합해 어휘를 만든다.
 * 정답 라벨 없이 통계만으로 어휘가 형성된다(self-supervised 전처리).
 */
object BpeTrainer {

    /**
     * @param corpus 학습 문서들
     * @param vocabSize 목표 어휘 크기(기저 256 포함). 예: 400
     * @param minFrequency 병합할 쌍의 최소 등장 횟수. 미만이면 조기 종료.
     */
    fun train(corpus: List<String>, vocabSize: Int, minFrequency: Int = 2): BpeModel {
        var sequences: List<MutableList<Int>> = corpus.map { text ->
            text.encodeToByteArray().map { it.toInt() and 0xFF }.toMutableList()
        }

        val ranks = LinkedHashMap<Pair<Int, Int>, Int>()
        val newIdOf = LinkedHashMap<Pair<Int, Int>, Int>()
        val mergedBytes = LinkedHashMap<Int, ByteArray>()
        var nextId = BpeModel.BASE_VOCAB
        var rank = 0

        fun bytesFor(id: Int): ByteArray =
            if (id < BpeModel.BASE_VOCAB) byteArrayOf(id.toByte()) else mergedBytes.getValue(id)

        while (nextId < vocabSize) {
            val counts = HashMap<Pair<Int, Int>, Int>()
            for (seq in sequences) {
                for (i in 0 until seq.size - 1) {
                    val pair = seq[i] to seq[i + 1]
                    counts[pair] = (counts[pair] ?: 0) + 1
                }
            }
            val best = counts.maxByOrNull { it.value } ?: break
            if (best.value < minFrequency) break

            val pair = best.key
            ranks[pair] = rank++
            newIdOf[pair] = nextId
            mergedBytes[nextId] = bytesFor(pair.first) + bytesFor(pair.second)

            sequences = sequences.map { applyMerge(it, pair, nextId) }
            nextId++
        }

        return BpeModel(ranks, newIdOf, mergedBytes)
    }

    /** 시퀀스에서 대상 쌍을 새 id 로 병합한다(왼쪽부터). */
    private fun applyMerge(seq: MutableList<Int>, pair: Pair<Int, Int>, newId: Int): MutableList<Int> {
        val out = ArrayList<Int>(seq.size)
        var i = 0
        while (i < seq.size) {
            if (i < seq.size - 1 && seq[i] == pair.first && seq[i + 1] == pair.second) {
                out.add(newId)
                i += 2
            } else {
                out.add(seq[i])
                i += 1
            }
        }
        return out
    }
}
