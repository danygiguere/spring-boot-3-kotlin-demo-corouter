package com.example.corouterdemo.exception

import com.example.corouterdemo.dto.ApiErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import org.springframework.context.MessageSource
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import java.util.Locale
import java.util.UUID

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

        data class ErrorDetail(
            val status: HttpStatus,
            val message: String,
            val errors: Map<String, List<String>>? = null,
        )

        val detail =
            when (ex) {
                // ============================================================
                // Custom app exceptions — messages are intentionally user-safe
                // and i18n-aware. ex: AppException.Conflict("error.username.taken")
                // ============================================================

                is AppException.ValidationErrors -> {
                    logAppException(ex, correlationId)
                    val summary = messageSource.getMessage(ex.messageKey, null, "Validation failed", locale)!!
                    val resolvedErrors =
                        ex.fieldErrors.mapValues { (_, keys) ->
                            keys.map { key -> messageSource.getMessage(key, null, key, locale)!! }
                        }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, resolvedErrors)
                }

                is AppException -> {
                    logAppException(ex, correlationId)
                    ErrorDetail(ex.httpStatus, resolveMessage(ex, locale))
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
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors)
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
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors)
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
                    logger.warn(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.BAD_REQUEST, resolveStatusMessage(HttpStatus.BAD_REQUEST, locale))
                }

                is NoSuchElementException -> {
                    logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.NOT_FOUND, resolveStatusMessage(HttpStatus.NOT_FOUND, locale))
                }

                is ResponseStatusException ->
                    if (ex.statusCode.is4xxClientError) {
                        logger.warn { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                        ErrorDetail(ex.statusCode as HttpStatus, resolveStatusMessage(ex.statusCode as HttpStatus, locale))
                    } else {
                        logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.reason}" }
                        ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, resolveStatusMessage(HttpStatus.INTERNAL_SERVER_ERROR, locale))
                    }

                else -> {
                    logger.error(ex) { "[$correlationId] ${ex.javaClass.simpleName}: ${ex.message}" }
                    ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, resolveStatusMessage(HttpStatus.INTERNAL_SERVER_ERROR, locale))
                }
            }

        val errorResponse =
            ApiErrorResponse(
                message = detail.message,
                code = detail.status.name,
                correlationId = correlationId,
                errors = detail.errors,
            )

        exchange.response.statusCode = detail.status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON

        val buffer = exchange.response.bufferFactory().wrap(objectMapper.writeValueAsBytes(errorResponse))
        return exchange.response.writeWith(Mono.just(buffer))
    }
}
