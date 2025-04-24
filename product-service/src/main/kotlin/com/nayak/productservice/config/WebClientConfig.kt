package com.nayak.productservice.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    private val log = LoggerFactory.getLogger(WebClientConfig::class.java)

    @Value("\${app.webclient.connect-timeout-ms:2000}")
    private val connectTimeoutMs: Int = 2000

    @Value("\${app.webclient.read-timeout-ms:5000}")
    private val readTimeoutSeconds: Long = 5L // Read timeout as long for handler

    // Note: Write timeout is less commonly needed unless sending large bodies
    // @Value("\${app.webclient.write-timeout-ms:5000}")
    // private val writeTimeoutSeconds: Long = 5L

    @Bean
    fun webClientBuilder(): WebClient.Builder {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
                // conn.addHandlerLast(WriteTimeoutHandler(writeTimeoutSeconds, TimeUnit.SECONDS)) // Uncomment if needed
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .filter(logRequest()) // Optional: Log outgoing requests
            .filter(logResponse()) // Optional: Log incoming responses/errors
    }

    // Optional filter function to log requests
    private fun logRequest(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
            log.info("=> Req: {} {}", clientRequest.method(), clientRequest.url())
            // Log headers if needed: clientRequest.headers().forEach { name, values -> log.info("   {}: {}", name, values) }
            // Log body if needed (requires careful handling of body consumption)
            Mono.just(clientRequest)
        }
    }

    // Optional filter function to log responses
    private fun logResponse(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofResponseProcessor { clientResponse ->
            log.info("<= Res Status: {}", clientResponse.statusCode())
            // Log headers if needed: clientResponse.headers().asHttpHeaders().forEach { name, values -> log.info("   {}: {}", name, values) }
            // Can't easily log body here without consuming it
            Mono.just(clientResponse)
        }
    }
}