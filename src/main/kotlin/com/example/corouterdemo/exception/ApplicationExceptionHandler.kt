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

    private fun resolveMessage(
        ex: AppException,
        locale: Locale,
    ): String = messageSource.getMessage(ex.messageKey, ex.messageArgs, ex.messageKey, locale) ?: ex.messageKey

    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        val correlationId = correlationId()

        data class ErrorDetail(
            val status: HttpStatus,
            val message: String,
            val errors: Map<String, List<String>>? = null,
        )

        val detail =
            when (ex) {
                // ===========================================
                // Custom exceptions — messages are user-safe
                // ===========================================

                is AppException.Unauthorized -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    logger.warn { "[$correlationId] Unauthorized: ${ex.messageKey}" }
                    ErrorDetail(HttpStatus.UNAUTHORIZED, resolveMessage(ex, locale))
                }

                is AppException.Forbidden -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    logger.warn { "[$correlationId] Forbidden: ${ex.messageKey}" }
                    ErrorDetail(HttpStatus.FORBIDDEN, resolveMessage(ex, locale))
                }

                is AppException.NotFound -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    logger.warn { "[$correlationId] NotFound: ${ex.messageKey}" }
                    ErrorDetail(HttpStatus.NOT_FOUND, resolveMessage(ex, locale))
                }

                is AppException.Conflict -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    logger.warn { "[$correlationId] Conflict: ${ex.messageKey}" }
                    ErrorDetail(HttpStatus.CONFLICT, resolveMessage(ex, locale))
                }

                is AppException.BadRequest -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    logger.warn { "[$correlationId] BadRequest: ${ex.messageKey}" }
                    ErrorDetail(HttpStatus.BAD_REQUEST, resolveMessage(ex, locale))
                }

                // =====================================================================
                // Framework exceptions — collect field errors or use generic messages
                // =====================================================================

                is ConstraintViolationException -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    val summary = messageSource.getMessage("validation.failed", null, locale)
                    val fieldErrors =
                        ex.constraintViolations
                            .groupBy({ it.propertyPath.toString() }, { it.message })
                    logger.warn {
                        "[$correlationId] Validation failed on ${exchange.request.method} ${exchange.request.path}: " +
                            fieldErrors.entries.joinToString(", ") { (field, messages) -> "$field: ${messages.joinToString("; ")}" }
                    }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors)
                }

                is WebExchangeBindException -> {
                    val locale = exchange.localeContext.locale ?: Locale.ENGLISH
                    val summary = messageSource.getMessage("validation.failed", null, locale)
                    val fieldErrors =
                        ex.bindingResult.fieldErrors
                            .groupBy({ it.field }, { it.defaultMessage ?: "Invalid value" })
                    logger.warn {
                        "[$correlationId] Validation failed on ${exchange.request.method} ${exchange.request.path}: " +
                            fieldErrors.entries.joinToString(", ") { (field, messages) -> "$field: ${messages.joinToString("; ")}" }
                    }
                    ErrorDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary, fieldErrors)
                }

                is ServerWebInputException -> {
                    logger.warn(ex) { "[$correlationId] ServerWebInputException: ${ex.reason}" }
                    ErrorDetail(HttpStatus.BAD_REQUEST, "The request could not be processed.")
                }

                is NoSuchElementException -> {
                    logger.warn { "[$correlationId] NoSuchElementException: ${ex.message}" }
                    ErrorDetail(HttpStatus.NOT_FOUND, "The requested resource was not found.")
                }

                is IllegalArgumentException -> {
                    logger.warn(ex) { "[$correlationId] IllegalArgumentException: ${ex.message}" }
                    ErrorDetail(HttpStatus.BAD_REQUEST, "The request could not be processed.")
                }

                is RuntimeException -> {
                    logger.error(ex) { "[$correlationId] RuntimeException: ${ex.message}" }
                    ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.")
                }

                else -> {
                    logger.error(ex) { "[$correlationId] Unhandled exception: ${ex.javaClass.simpleName} - ${ex.message}" }
                    ErrorDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.")
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
