package com.llmgateway.tokenizer.bpe

/**
 * 번들 코퍼스에서 BPE 모델을 학습해 BpeTokenizer 를 만든다.
 * 조립 계층(bootstrap)이 자체 코퍼스·vocab 을 준비할 필요 없이 기본값을 쓰게 한다.
 */
object BpeTokenizerFactory {

    private const val CORPUS_RESOURCE = "/bpe-corpus.txt"

    /**
     * 번들 코퍼스로 학습한 기본 토크나이저.
     * @param vocabSize 목표 어휘 크기(기저 256 포함).
     */
    fun default(vocabSize: Int = 512): BpeTokenizer {
        val corpus = loadCorpus()
        val model = BpeTrainer.train(corpus, vocabSize)
        return BpeTokenizer(model)
    }

    private fun loadCorpus(): List<String> {
        val stream = BpeTokenizerFactory::class.java.getResourceAsStream(CORPUS_RESOURCE)
            ?: error("BPE 코퍼스 리소스를 찾을 수 없다: $CORPUS_RESOURCE")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        // 줄 단위를 학습 문서로 쓴다. 빈 줄 제외.
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}
