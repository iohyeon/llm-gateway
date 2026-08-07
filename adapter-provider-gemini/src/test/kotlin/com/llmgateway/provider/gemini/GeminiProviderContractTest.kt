package com.llmgateway.provider.gemini

import com.fasterxml.jackson.databind.ObjectMapper
import com.llmgateway.core.decode.DecodePreset
import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.model.Role
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.client.reactive.JdkClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gemini 어댑터 계약 테스트.
 * 요청 바디(systemInstruction 승격, assistant→model 변환, top_k 포함/제외)와 SSE 파싱을 검증한다.
 */
class GeminiProviderContractTest {

    private lateinit var server: MockWebServer
    private val mapper = ObjectMapper()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(): GeminiProvider {
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString())
            .clientConnector(JdkClientHttpConnector())
            .build()
        return GeminiProvider(webClient = webClient, model = "gemini-test")
    }

    private fun sse(body: String) =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `buildBody 는 systemInstruction 승격과 assistant to model 변환, top_k 포함을 수행한다`() {
        server.enqueue(sse("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}\n\n"))
        val request = CompletionRequest(
            messages = listOf(
                ChatMessage(Role.SYSTEM, "sys"),
                ChatMessage(Role.USER, "hi"),
                ChatMessage(Role.ASSISTANT, "prev"),
            ),
            preset = DecodePreset.CREATIVE, // temperature=1.0, topP=0.95, topK=40
            maxOutputTokens = 256,
        )

        runBlocking { provider().complete(request).toList() }

        val body = mapper.readTree(server.takeRequest().body.readUtf8())

        val contents = body.get("contents")
        assertEquals(2, contents.size()) // system 은 contents 에서 제외
        assertEquals("user", contents.get(0).get("role").asText())
        assertEquals("model", contents.get(1).get("role").asText()) // assistant → model
        assertEquals("hi", contents.get(0).get("parts").get(0).get("text").asText())

        assertEquals(
            "sys",
            body.get("systemInstruction").get("parts").get(0).get("text").asText(),
        )

        val gen = body.get("generationConfig")
        assertEquals(1.0, gen.get("temperature").asDouble())
        assertEquals(0.95, gen.get("topP").asDouble())
        assertEquals(40, gen.get("topK").asInt()) // Gemini 는 top_k 를 받는다
        assertEquals(256, gen.get("maxOutputTokens").asInt())
    }

    @Test
    fun `프리셋에 top_k 가 없으면 generationConfig 에서 제외한다`() {
        server.enqueue(sse("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}\n\n"))
        val request = CompletionRequest(
            messages = listOf(ChatMessage(Role.USER, "hi")),
            preset = DecodePreset.BALANCED, // topK = null
        )

        runBlocking { provider().complete(request).toList() }

        val gen = mapper.readTree(server.takeRequest().body.readUtf8()).get("generationConfig")
        assertFalse(gen.has("topK"))
        assertTrue(gen.has("temperature"))
    }

    @Test
    fun `SSE 는 candidates parts text 를 방출한다`() {
        val stream =
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Yo\"}]}}]}\n\n" +
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"!\"}]}}]}\n\n"
        server.enqueue(sse(stream))

        val texts = runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
            .map { it.text }

        assertEquals(listOf("Yo", "!"), texts)
    }

    @Test
    fun `error 오브젝트는 예외로 전파한다`() {
        server.enqueue(sse("data: {\"error\":{\"message\":\"quota\"}}\n\n"))

        assertFailsWith<GeminiStreamException> {
            runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
        }
    }
}
