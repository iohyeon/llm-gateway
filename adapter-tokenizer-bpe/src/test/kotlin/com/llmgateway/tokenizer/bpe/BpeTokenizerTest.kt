package com.llmgateway.tokenizer.bpe

import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BpeTokenizerTest {

    @Test
    fun `자주 붙는 쌍을 병합해 어휘를 키운다`() {
        // "ab" 가 반복되면 (a,b) 병합이 학습되어 어휘가 256 을 넘는다.
        val model = BpeTrainer.train(listOf("ababab", "abab"), vocabSize = 300)
        assertTrue(model.vocabSize > BpeModel.BASE_VOCAB)
        val aByte = 'a'.code
        val bByte = 'b'.code
        assertTrue(model.ranks.containsKey(aByte to bByte), "가장 흔한 쌍 (a,b) 가 병합되어야 한다")
    }

    @Test
    fun `병합으로 시퀀스 길이가 줄어든다`() {
        val model = BpeTrainer.train(listOf("ababababab"), vocabSize = 300)
        val tokenizer = BpeTokenizer(model)
        val ids = tokenizer.encode("ababab")
        assertTrue(ids.size < 6, "병합 후 토큰 수가 원바이트 6개보다 적어야 한다")
    }

    @Test
    fun `encode 후 decode 하면 원문이 복원된다 (영문)`() {
        val model = BpeTrainer.train(listOf("the quick brown fox", "the lazy dog"), vocabSize = 320)
        val tokenizer = BpeTokenizer(model)
        val text = "the fox and the dog"
        assertEquals(text, tokenizer.decode(tokenizer.encode(text)))
    }

    @Test
    fun `학습에 없던 문자도 바이트 단위로 복원된다 (OOV 없음)`() {
        val model = BpeTrainer.train(listOf("abcabc"), vocabSize = 300)
        val tokenizer = BpeTokenizer(model)
        // 한글은 학습 코퍼스에 없지만 byte-level 이라 round-trip 이 보존된다.
        val text = "안녕 z"
        assertEquals(text, tokenizer.decode(tokenizer.encode(text)))
    }

    @Test
    fun `countMessages 는 메시지당 오버헤드를 더한다`() {
        val tokenizer = BpeTokenizerFactory.default(vocabSize = 400)
        val one = tokenizer.countMessages(listOf(ChatMessage(Role.USER, "안녕하세요")))
        val two = tokenizer.countMessages(
            listOf(ChatMessage(Role.USER, "안녕하세요"), ChatMessage(Role.ASSISTANT, "반갑습니다")),
        )
        assertTrue(one.value >= 1)
        assertTrue(two.value > one.value)
    }
}
