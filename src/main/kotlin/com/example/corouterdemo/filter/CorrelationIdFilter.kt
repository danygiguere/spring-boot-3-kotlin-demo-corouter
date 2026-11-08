package com.example.corouterdemo.filter

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Stamps every request with a server-generated correlation id, echoed in the [CORRELATION_ID_HEADER] response
 * header and reused by the error handler, so a problem-detail `correlationId` matches the header and the log
 * lines. Inbound values are ignored (not trusted into logs).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        exchange.response.headers.set(CORRELATION_ID_HEADER, UUID.randomUUID().toString())
        return chain.filter(exchange)
    }

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"

        /** The id stamped by this filter; a fresh one if the exchange bypassed it. */
        fun of(exchange: ServerWebExchange): String =
            exchange.response.headers.getFirst(CORRELATION_ID_HEADER)
                ?: UUID.randomUUID().toString().also { exchange.response.headers.set(CORRELATION_ID_HEADER, it) }
    }
}
