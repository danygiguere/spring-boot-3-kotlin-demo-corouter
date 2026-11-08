package com.example.corouterdemo.exception

import com.example.corouterdemo.filter.CorrelationIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.net.URI
import java.util.Locale
import java.util.concurrent.CancellationException

private val logger = KotlinLogging.logger {}

/**
 * Maps every uncaught exception to an RFC 9457 problem-detail response.
 *
 * Client-visible messages are an allowlist, not a denylist: [AppException] is the only type whose message (an i18n
 * key resolved via [MessageSource]) reaches the client. Any other exception may carry internals (SQL, paths, state),
 * so its message is never exposed: the client receives a generic status phrase. A [ResponseStatusException] keeps
 * its 4xx status code, but never its text. The real message and stack trace go to the log, tied to the response by
 * the correlationId.
 */
@Component
@Order(-2)
class ApplicationExceptionHandler(
    private val objectMapper: ObjectMapper,
    private val messageSource: MessageSource,
) : WebExceptionHandler {
    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        // Client disconnected mid-request — not a server error, and nothing to write to.
        if (ex is CancellationException) return Mono.empty()
        if (exchange.response.isCommitted) {
            // Headers already sent (error mid-stream): the status can no longer change.
            logger.error(ex) { "[${CorrelationIdFilter.of(exchange)}] Error after response was committed" }
            return Mono.error(ex)
        }

        val correlationId = CorrelationIdFilter.of(exchange)
        val locale = exchange.localeContext.locale ?: Locale.ENGLISH

        val problem = toProblem(exchange, ex, correlationId, locale)
        problem.instance = URI.create(exchange.request.path.value())
        problem.setProperty("correlationId", correlationId)

        exchange.response.statusCode = HttpStatusCode.valueOf(problem.status)
        exchange.response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        val buffer = exchange.response.bufferFactory().wrap(objectMapper.writeValueAsBytes(problem))
        return exchange.response.writeWith(Mono.just(buffer))
    }

    private fun toProblem(
        exchange: ServerWebExchange,
        ex: Throwable,
        correlationId: String,
        locale: Locale,
    ): ProblemDetail =
        when (ex) {
            // Business field errors that Bean Validation annotations cannot express.
            is AppException.ValidationErrors -> {
                logAppException(ex, correlationId)
                val resolved = ex.fieldErrors.mapValues { (_, keys) -> keys.map { messageSource.getMessage(it, null, it, locale)!! } }
                ex.updateAndGetBody(messageSource, locale).apply { setProperty("errors", resolved) }
            }

            // Custom app exceptions — messages are intentionally user-safe and i18n-aware.
            is AppException -> {
                logAppException(ex, correlationId)
                ex.updateAndGetBody(messageSource, locale)
            }

            // Thrown by Validator.validateAndThrow in handlers.
            is ConstraintViolationException -> {
                val fieldErrors =
                    ex.constraintViolations.groupBy(
                        { it.propertyPath.toString().replace(LIST_ELEMENT_NODE, "") },
                        { it.message },
                    )
                logger.warn {
                    "[$correlationId] Validation failed on ${exchange.request.method} ${exchange.request.path}: " +
                        fieldErrors.entries.joinToString(", ") { (field, messages) -> "$field: ${messages.joinToString("; ")}" }
                }
                validationProblem(fieldErrors, locale)
            }

            // Body larger than spring.codec.max-in-memory-size.
            is DataBufferLimitException -> {
                logger.warn { "[$correlationId] Request body too large: ${ex.message}" }
                problemOf(HttpStatus.PAYLOAD_TOO_LARGE, locale)
            }

            // Includes ServerWebInputException (malformed JSON, unknown field) and 404/405/415 from routing.
            is ResponseStatusException ->
                if (ex.statusCode.is4xxClientError) {
                    logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                    problemOf(HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.BAD_REQUEST, locale)
                } else {
                    logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                    problemOf(HttpStatus.INTERNAL_SERVER_ERROR, locale)
                }

            else -> {
                logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                problemOf(HttpStatus.INTERNAL_SERVER_ERROR, locale)
            }
        }

    private fun validationProblem(
        fieldErrors: Map<String, List<String>>,
        locale: Locale,
    ): ProblemDetail {
        val summary = messageSource.getMessage("error.validation_failed", null, "Validation failed", locale)!!
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary).apply {
            type = AppException.typeUri("error.validation_failed")
            setProperty("errors", fieldErrors)
        }
    }

    /** Generic phrase per status: never the exception's technical message. */
    private fun problemOf(
        status: HttpStatus,
        locale: Locale,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            status,
            messageSource.getMessage("error.${status.value()}", null, status.reasonPhrase, locale)!!,
        )

    private fun logAppException(
        ex: AppException,
        correlationId: String,
    ) {
        val label = "AppException.${ex::class.simpleName}"
        if (ex.cause != null) {
            logger.warn(ex) { "[$correlationId] $label: ${ex.messageKey}" }
        } else {
            logger.warn { "[$correlationId] $label: ${ex.messageKey}" }
        }
    }

    private companion object {
        const val LIST_ELEMENT_NODE = ".<list element>"
    }
}
