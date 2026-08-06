package com.llmgateway.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 공급자 아웃바운드 WebClient 를 회복탄력성 정책과 함께 조립한다.
 * architecture.md 가 게이트웨이의 존재 이유로 드는 "흩어진 타임아웃/재시도의 집약"을
 * 실제 배선으로 구현하는 지점. 세 공급자 어댑터가 동일 정책을 공유한다.
 *
 * 스트리밍 재시도 경계(핵심 안전 설계):
 *  재시도는 ExchangeFilterFunction 안, 즉 응답 바디 스트림이 시작되기 "전" 단계에만 건다.
 *  일단 응답(헤더)을 받으면 재시도 없이 바디를 그대로 흘려보낸다. 따라서 토큰이 한 조각이라도
 *  다운스트림으로 방출된 뒤 발생한 오류는 구조적으로 재시도되지 않는다(부분 방출 후 재실행에 의한
 *  토큰 중복/오염 방지). 재시도가 커버하는 것은 연결 거부/응답 타임아웃/헤더 수신 전 5xx 처럼
 *  아직 아무 토큰도 흐르지 않은 실패뿐이다.
 */
object ProviderWebClients {

    /**
     * baseUrl 과 회복탄력성 정책이 적용된 WebClient.Builder 를 돌려준다.
     * 호출부는 공급자별 인증 헤더만 더해 build() 한다.
     */
    fun resilientBuilder(baseUrl: String, resilience: ProviderResilience): WebClient.Builder {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, resilience.connectTimeoutMillis)
            .responseTimeout(Duration.ofMillis(resilience.responseTimeoutMillis))
            .doOnConnected { conn ->
                conn.addHandlerLast(
                    ReadTimeoutHandler(resilience.readIdleTimeoutMillis, TimeUnit.MILLISECONDS),
                )
            }

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .filter(transientRetryFilter(resilience.maxRetries, resilience.retryBackoffMillis))
    }

    /**
     * 일시적 오류 한정 재시도 필터.
     * 5xx 응답은 예외로 변환해 재시도 트리거로 삼는다(4xx 는 그대로 통과시켜 재시도하지 않는다).
     * 재시도 소진 시 마지막 원인 예외를 그대로 던져 상위 매핑(예: WebClientResponseException → 502)을 보존한다.
     */
    private fun transientRetryFilter(maxRetries: Long, backoffMillis: Long): ExchangeFilterFunction =
        ExchangeFilterFunction { request, next ->
            next.exchange(request)
                .flatMap { response ->
                    if (response.statusCode().is5xxServerError) {
                        // 5xx 는 바디를 읽어 예외로 변환(재시도 대상). 성공/4xx 응답은 그대로 통과.
                        response.createException().flatMap { Mono.error<ClientResponse>(it) }
                    } else {
                        Mono.just(response)
                    }
                }
                .retryWhen(
                    Retry.backoff(maxRetries, Duration.ofMillis(backoffMillis))
                        .filter { isTransient(it) }
                        .onRetryExhaustedThrow { _, signal -> signal.failure() },
                )
        }

    /**
     * 일시적(재시도 가능) 오류 판정.
     *  - 5xx 업스트림 응답: 서버 측 일시 장애로 간주.
     *  - 연결 계층 오류(거부/리셋/DNS 등): WebClientRequestException 및 하위 IOException.
     *  - 응답 타임아웃/유휴 타임아웃: TimeoutException.
     * 4xx 는 클라이언트 오류이므로 재시도하지 않는다(여기서 false → 즉시 전파).
     */
    private fun isTransient(error: Throwable): Boolean = when (error) {
        is WebClientResponseException -> error.statusCode.is5xxServerError
        is WebClientRequestException -> true
        is TimeoutException -> true
        is IOException -> true
        else -> false
    }
}
