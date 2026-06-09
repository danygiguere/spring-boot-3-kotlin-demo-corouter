package com.example.corouterdemo.exception

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Maps every uncaught exception to an RFC 9457 problem-detail response.
 *
 * Client-visible messages are an allowlist, not a denylist: [AppException] is the only
 * type whose message (an i18n key resolved via [MessageSource]) reaches the client.
 * Any other exception may carry internals (SQL, paths, state), so its message is never
 * exposed. The client receives a generic status phrase. A known built-in type can still
 * map to a specific status code (e.g. NoSuchElementException → 404), but only the code
 * is taken from it, never the text. The real message and stack trace go to the log,
 * tied to the response by the correlationId.
 */
@Component
@Order(-2)
class ApplicationExceptionHandler(
    private val objectMapper: ObjectMapper,
    private val messageSource: MessageSource,
) : WebExceptionHandler {
    private val logger = KotlinLogging.logger {}

    private fun correlationId() = UUID.randomUUID().toString()

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

    private fun resolveMessage(
        ex: AppException,
        locale: Locale,
    ): String = messageSource.getMessage(ex.messageKey, ex.args.ifEmpty { null }, ex.messageKey, locale)!!

    private fun resolveStatusMessage(
        status: HttpStatus,
        locale: Locale,
    ): String = messageSource.getMessage("error.${status.value()}", null, status.reasonPhrase, locale)!!

    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        val correlationId = correlationId()
        val locale = exchange.localeContext.locale ?: Locale.ENGLISH

        // typeKey feeds the RFC 9457 "type" URI — a stable, locale-independent
        // identifier clients can switch on (the localized "detail" text cannot
        // be matched programmatically). Null means the error category is
        // deliberately opaque and "type" stays "about:blank".
        data class ErrorDetail(
            val status: HttpStatus,
            val message: String,
            val errors: Map<String, List<String>>? = null,
            val typeKey: String? = null,
        )

        val detail =
            when (ex) {
                // ============================================================
                // Custom app exceptions — messages are intentionally user-safe
                // and i18n-aware. ex: AppException.Conflict("error.username.taken")
                // ============================================================

                // AppException.ValidationErrors is the manual counterpart to the
                // framework-driven validation below (ConstraintViolationException /
                // WebExchangeBindException). Use it when business logic detects
                // field errors that Bean Validation annotations cannot express
                // (e.g. cross-field rules, DB uniqueness checks on update).
                is AppException.ValidationErrors -> {
                    logAppException(ex, correlationId)
                    val summary = messageSource.getMessage(ex.messageKey, null, "Validation failed", locale)!!
                    val resolvedErrors =
                        ex.fieldErrors.mapValues { (_, keys) ->
                            keys.map { key -> messageSource.getMessage(key, null, key, locale)!! }
                        }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, resolvedErrors, ex.messageKey)
                }

                is AppException -> {
                    logAppException(ex, correlationId)
                    ErrorDetail(ex.httpStatus, resolveMessage(ex, locale), typeKey = ex.messageKey)
                }

                // ============================================================
                // Input validation — field errors are preserved and returned
                // ============================================================

                // ConstraintViolationException — triggered by @Validated on service/handler method parameters
                is ConstraintViolationException -> {
                    val summary = messageSource.getMessage("error.validation_failed", null, "Validation failed", locale)!!
                    val fieldErrors =
                        ex.constraintViolations
                            .groupBy(
                                { it.propertyPath.toString() },
                                { messageSource.getMessage(it.message, null, it.message, locale)!! },
                            )
                    logger.warn {
                        "[$correlationId] Validation failed on ${exchange.request.method} ${exchange.request.path}: " +
                            fieldErrors.entries.joinToString(", ") { (field, messages) -> "$field: ${messages.joinToString("; ")}" }
                    }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors, "error.validation_failed")
                }

                // WebExchangeBindException — triggered by @Valid on @RequestBody in WebFlux
                is WebExchangeBindException -> {
                    val summary = messageSource.getMessage("error.validation_failed", null, "Validation failed", locale)!!
                    val fieldErrors =
                        ex.bindingResult.fieldErrors
                            .groupBy({ it.field }, {
                                messageSource.getMessage(it, locale)
                                    ?: messageSource.getMessage("validation.invalid.value", null, "Invalid value", locale)!!
                            })
                    logger.warn {
                        "[$correlationId] Validation failed on ${exchange.request.method} ${exchange.request.path}: " +
                            fieldErrors.entries.joinToString(", ") { (field, messages) -> "$field: ${messages.joinToString("; ")}" }
                    }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors, "error.validation_failed")
                }

                // ============================================================
                // Framework exceptions — internal messages are suppressed and
                // replaced with generic responses to avoid leaking internals
                // ============================================================

                is AccessDeniedException -> {
                    logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.FORBIDDEN, resolveStatusMessage(HttpStatus.FORBIDDEN, locale))
                }

                is ServerWebInputException, is IllegalArgumentException -> {
                    logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.BAD_REQUEST, resolveStatusMessage(HttpStatus.BAD_REQUEST, locale))
                }

                is NoSuchElementException -> {
                    logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.NOT_FOUND, resolveStatusMessage(HttpStatus.NOT_FOUND, locale))
                }

                is ResponseStatusException ->
                    if (ex.statusCode.is4xxClientError) {
                        logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.BAD_REQUEST
                        ErrorDetail(status, resolveStatusMessage(status, locale))
                    } else {
                        logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                        ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, resolveStatusMessage(HttpStatus.INTERNAL_SERVER_ERROR, locale))
                    }

                else -> {
                    logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, resolveStatusMessage(HttpStatus.INTERNAL_SERVER_ERROR, locale))
                }
            }

        val problem = ProblemDetail.forStatus(detail.status)
        problem.detail = detail.message
        problem.instance = URI.create(exchange.request.path.value())
        // "error.username.taken" → "/errors/username-taken". Message keys are
        // thereby part of the API contract: renaming one changes the type URI.
        detail.typeKey?.let { key ->
            problem.type = URI.create("/errors/" + key.removePrefix("error.").replace('.', '-').replace('_', '-'))
        }
        problem.setProperty("correlationId", correlationId)
        detail.errors?.let { problem.setProperty("errors", it) }

        exchange.response.statusCode = detail.status
        exchange.response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON

        val buffer = exchange.response.bufferFactory().wrap(objectMapper.writeValueAsBytes(problem))
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
