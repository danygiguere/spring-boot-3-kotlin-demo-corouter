package com.example.corouterdemo.filter

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Gives every request a correlation ID and puts request details in the MDC for every log line.
 *
 * - The ID is echoed in the `X-Correlation-ID` response header and in error bodies, so a user can
 *   report it and an operator can find the matching log lines, including internal messages the
 *   client never sees.
 * - MDC keys: `correlationId`, `requestUri`, `requestMethod`.
 *
 * WebFlux has no thread per request, so putting values in the MDC of the current thread is not
 * enough: a `suspend` handler that switches threads (for example to `Dispatchers.IO`) loses them.
 * Instead, the rest of the chain runs inside an [MDCContext]. Because this is a [CoWebFilter],
 * Spring reuses that coroutine context when it calls `suspend` controller methods, and
 * [MDCContext] restores the MDC on whatever thread the coroutine resumes on. The ID is also stored
 * as an exchange attribute, which the exception handler reads.
 *
 * An incoming `X-Correlation-ID` is reused only if it matches a strict pattern. Anything else is
 * replaced, so a client cannot inject newlines or fake entries into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain,
    ) {
        val incoming = exchange.request.headers.getFirst(CORRELATION_ID_HEADER)
        val correlationId = incoming?.takeIf { CORRELATION_ID_PATTERN.matches(it) } ?: newCorrelationId()
        exchange.attributes[ATTRIBUTE] = correlationId
        exchange.response.headers.set(CORRELATION_ID_HEADER, correlationId)
        val mdc =
            mapOf(
                MDC_CORRELATION_ID to correlationId,
                "requestUri" to exchange.request.path.value(), // never the query string, which may hold tokens
                "requestMethod" to exchange.request.method.name(),
            )
        withContext(MDCContext(mdc)) {
            chain.filter(exchange)
        }
    }

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"
        const val MDC_CORRELATION_ID = "correlationId"
        private val ATTRIBUTE = RequestLoggingFilter::class.qualifiedName + ".correlationId"
        private val CORRELATION_ID_PATTERN = Regex("^[a-zA-Z0-9\\-]{1,64}$")

        /**
         * Correlation IDs need to be unique, not unpredictable. `UUID.randomUUID()` reads the
         * platform SecureRandom, which can block the event loop. ThreadLocalRandom does no I/O.
         */
        fun newCorrelationId(): String {
            val random = ThreadLocalRandom.current()
            return UUID(random.nextLong(), random.nextLong()).toString()
        }

        fun currentCorrelationId(exchange: ServerWebExchange): String? = exchange.getAttribute(ATTRIBUTE)
    }
}
