package com.llmgateway.provider.anthropic

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
 * Anthropic 어댑터 계약 테스트.
 * 요청 바디(system 승격, 샘플링 파라미터 생략)와 SSE 파싱(text_delta/error)을 실제 와이어로 검증한다.
 */
class AnthropicProviderContractTest {

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

    private fun provider(): AnthropicProvider {
        val webClient = WebClient.builder()
            .baseUrl(server.url("/").toString())
            .clientConnector(JdkClientHttpConnector())
            .build()
        return AnthropicProvider(webClient = webClient, model = "claude-test")
    }

    private fun sse(body: String) =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `buildBody 는 system 을 top-level 로 승격하고 샘플링 파라미터를 생략한다`() {
        server.enqueue(sse("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"))
        val request = CompletionRequest(
            messages = listOf(
                ChatMessage(Role.SYSTEM, "you are helpful"),
                ChatMessage(Role.USER, "hi"),
                ChatMessage(Role.ASSISTANT, "prev"),
            ),
            preset = DecodePreset.CREATIVE, // topK 를 가진 프리셋이라도 Anthropic 경로는 샘플링을 생략해야 한다
            maxOutputTokens = 256,
        )

        runBlocking { provider().complete(request).toList() }

        val body = mapper.readTree(server.takeRequest().body.readUtf8())
        assertEquals("you are helpful", body.get("system").asText())
        assertEquals("claude-test", body.get("model").asText())
        assertEquals(256, body.get("max_tokens").asInt())
        assertTrue(body.get("stream").asBoolean())

        val messages = body.get("messages")
        assertEquals(2, messages.size()) // system 은 messages 에서 제외
        assertEquals("user", messages.get(0).get("role").asText())
        assertEquals("assistant", messages.get(1).get("role").asText())

        assertFalse(body.has("temperature"))
        assertFalse(body.has("top_p"))
        assertFalse(body.has("top_k"))
    }

    @Test
    fun `SSE 는 text_delta 만 토큰으로 방출하고 그 외 프레임은 무시한다`() {
        val stream =
            "event: message_start\ndata: {\"type\":\"message_start\"}\n\n" +
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"He\"}}\n\n" +
                "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"llo\"}}\n\n" +
                "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
        server.enqueue(sse(stream))

        val texts = runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
            .map { it.text }

        assertEquals(listOf("He", "llo"), texts)
    }

    @Test
    fun `error 프레임은 예외로 전파한다`() {
        val stream =
            "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"boom\"}}\n\n"
        server.enqueue(sse(stream))

        assertFailsWith<AnthropicStreamException> {
            runBlocking { provider().complete(CompletionRequest(listOf(ChatMessage(Role.USER, "hi")))).toList() }
        }
    }
}
