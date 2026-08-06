package com.llmgateway.config

import com.llmgateway.core.model.ChatMessage
import com.llmgateway.core.model.CompletionRequest
import com.llmgateway.core.model.Role
import com.llmgateway.provider.openai.OpenAiProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.TimeUnit

/**
 * 회복탄력성 계약 테스트. ProviderWebClients 로 조립한 WebClient 를 실제 공급자 어댑터에 물려
 * MockWebServer 의 지연/오류에 대한 타임아웃·재시도 경계를 검증한다.
 * 스트리밍 안전 설계(부분 방출 후 재시도 금지)를 실제 소켓 이벤트로 증명하는 것이 핵심이다.
 */
class ProviderWebClientsResilienceTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private val request = CompletionRequest(messages = listOf(ChatMessage(Role.USER, "hi")))

    private fun provider(resilience: ProviderResilience): OpenAiProvider {
        val webClient = ProviderWebClients
            .resilientBuilder(server.url("/").toString(), resilience)
            .build()
        return OpenAiProvider(webClient = webClient, model = "gpt-test")
    }

    private fun retrying(maxRetries: Long = 2) = ProviderResilience(
        connectTimeoutMillis = 2_000,
        responseTimeoutMillis = 5_000,
        readIdleTimeoutMillis = 5_000,
        maxRetries = maxRetries,
        retryBackoffMillis = 10,
    )

    private fun sse(body: String) =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    private val okStream =
        "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
            "data: [DONE]\n\n"

    @Test
    fun `일시적 5xx 는 재시도 후 성공한다`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("overloaded"))
        server.enqueue(sse(okStream))

        val texts = runBlocking { provider(retrying()).complete(request).toList() }.map { it.text }

        assertEquals(listOf("Hi"), texts)
        assertEquals(2, server.requestCount) // 503 1회 + 재시도 성공 1회
    }

    @Test
    fun `4xx 는 재시도하지 않고 그대로 전파한다`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("bad request"))

        val ex = assertThrows(WebClientResponseException::class.java) {
            runBlocking { provider(retrying()).complete(request).toList() }
        }

        assertEquals(400, ex.statusCode.value())
        assertEquals(1, server.requestCount) // 클라이언트 오류는 재시도 대상이 아니다
    }

    @Test
    fun `지속 5xx 는 한정 재시도 후 소진되어 원인 예외를 던진다`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(500)) } // maxRetries=2 → 시도 3회

        val ex = assertThrows(WebClientResponseException::class.java) {
            runBlocking { provider(retrying(maxRetries = 2)).complete(request).toList() }
        }

        assertEquals(500, ex.statusCode.value())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `응답을 시작조차 않는 업스트림은 응답 타임아웃으로 끊는다`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val resilience = ProviderResilience(
            connectTimeoutMillis = 2_000,
            responseTimeoutMillis = 400,
            readIdleTimeoutMillis = 5_000,
            maxRetries = 0,
            retryBackoffMillis = 10,
        )

        assertThrows(Exception::class.java) {
            runBlocking { provider(resilience).complete(request).toList() }
        }
    }

    @Test
    fun `헤더 수신 후 바디가 멎으면 읽기 유휴 타임아웃으로 끊는다`() {
        // 헤더는 즉시, 바디는 2초 지연. read-idle(300ms)이 먼저 발화해 무한 대기를 차단한다.
        server.enqueue(sse(okStream).setBodyDelay(2, TimeUnit.SECONDS))
        val resilience = ProviderResilience(
            connectTimeoutMillis = 2_000,
            responseTimeoutMillis = 5_000,
            readIdleTimeoutMillis = 300,
            maxRetries = 0,
            retryBackoffMillis = 10,
        )

        assertThrows(Exception::class.java) {
            runBlocking { provider(resilience).complete(request).toList() }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `토큰이 흐른 뒤의 스트림 중단은 재시도하지 않는다`() {
        // 완전한 프레임 1개(토큰 A) 뒤에 패딩을 두고 바디 도중 연결을 끊는다.
        // 재시도(maxRetries=2)를 허용해도, 바디가 시작된 뒤의 오류는 필터 경계 밖이라 재실행되지 않는다.
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}\n\n" +
            ": " + "x".repeat(4_000) + "\n\n"
        server.enqueue(
            sse(body).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val received = mutableListOf<String>()
        runCatching {
            runBlocking { provider(retrying(maxRetries = 2)).complete(request).collect { received.add(it.text) } }
        }

        assertEquals(1, server.requestCount) // 바디 시작 후 오류는 재시도되지 않는다
        assertEquals(1, received.count { it == "A" }) // 부분 방출 후 재실행에 의한 중복 없음
    }
}
