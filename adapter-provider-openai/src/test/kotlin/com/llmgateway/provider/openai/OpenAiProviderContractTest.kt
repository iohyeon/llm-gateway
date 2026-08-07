package com.llmgateway.provider.openai

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
 * OpenAI 어댑터 계약 테스트.
 * 요청 바디(system 비승격, temperature/top_p 번역, top_k 제외)와 SSE 파싱([DONE]/error)을 검증한다.
 */
class OpenAiProviderContractTest {

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

    private fun provider(): OpenAiProvider {
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString())
            .clientConnector(JdkClientHttpConnector())
            .build()
        return OpenAiProvider(webClient = webClient, model = "gpt-test")
    }

    private fun sse(body: String) =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `buildBody 는 system 을 그대로 두고 temperature-top_p 를 번역하되 top_k 는 제외한다`() {
        server.enqueue(sse("data: [DONE]\n\n"))
        val request = CompletionRequest(
            messages = listOf(
                ChatMessage(Role.SYSTEM, "sys"),
                ChatMessage(Role.USER, "hi"),
            ),
            preset = DecodePreset.CREATIVE, // temperature=1.0, topP=0.95, topK=40
            maxOutputTokens = 128,
        )

        runBlocking { provider().complete(request).toList() }

        val body = mapper.readTree(server.takeRequest().body.readUtf8())
        assertEquals("gpt-test", body.get("model").asText())
        assertEquals(128, body.get("max_tokens").asInt())
        assertTrue(body.get("stream").asBoolean())
        assertEquals(1.0, body.get("temperature").asDouble())
        assertEquals(0.95, body.get("top_p").asDouble())
        assertFalse(body.has("top_k")) // OpenAI Chat Completions 미지원 → 번역에서 제외

        val messages = body.get("messages")
        assertEquals(2, messages.size()) // system 을 top-level 로 승격하지 않는다
        assertEquals("system", messages.get(0).get("role").asText())
        assertEquals("user", messages.get(1).get("role").asText())
    }

    @Test
    fun `SSE 는 delta content 를 방출하고 DONE 은 무시한다`() {
        val stream =
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\"!\"}}]}\n\n" +
                "data: [DONE]\n\n"
        server.enqueue(sse(stream))

        val texts = runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
            .map { it.text }

        assertEquals(listOf("Hi", "!"), texts)
    }

    @Test
    fun `error 오브젝트는 예외로 전파한다`() {
        server.enqueue(sse("data: {\"error\":{\"message\":\"rate\"}}\n\n"))

        assertFailsWith<OpenAiStreamException> {
            runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
        }
    }
}
