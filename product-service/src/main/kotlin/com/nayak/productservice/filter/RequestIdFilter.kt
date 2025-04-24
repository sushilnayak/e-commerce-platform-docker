package com.nayak.productservice.filter

import com.fasterxml.uuid.Generators
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context

@Component
class RequestIdFilter : WebFilter {

    private val log = LoggerFactory.getLogger(RequestIdFilter::class.java)
    private val generator = Generators.timeBasedGenerator()

    companion object {
        const val REQUEST_ID_KEY = "requestId"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = generator.generate().toString()

        exchange.response.headers.add(REQUEST_ID_KEY, requestId)

        log.debug("Generated Request ID: {}", requestId)

        return chain.filter(exchange)
            .contextWrite { context: Context ->
                context.put(REQUEST_ID_KEY, requestId)
            }
    }
}