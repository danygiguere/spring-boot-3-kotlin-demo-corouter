package com.example.corouterdemo.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI

/**
 * Client-safe error. [messageKey] is resolved via `MessageSource` into the problem "detail"
 * (see [updateAndGetBody]) and also feeds the stable "type" URI (see [typeUri]).
 */
sealed class AppException(
    status: HttpStatus,
    val messageKey: String,
    args: Array<Any> = emptyArray(),
    cause: Throwable? = null,
) : ErrorResponseException(status, ProblemDetail.forStatus(status), cause, messageKey, args) {
    init {
        body.detail = messageKey
        body.type = typeUri(messageKey)
    }

    class NotFound(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(HttpStatus.NOT_FOUND, messageKey, args, cause)

    class Conflict(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(HttpStatus.CONFLICT, messageKey, args, cause)

    class BadRequest(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(HttpStatus.BAD_REQUEST, messageKey, args, cause)

    class Unauthorized(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(HttpStatus.UNAUTHORIZED, messageKey, args, cause)

    class Forbidden(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(HttpStatus.FORBIDDEN, messageKey, args, cause)

    /**
     * Use when business logic detects multiple field-level errors that cannot be expressed
     * with Bean Validation annotations (e.g. cross-field rules, DB uniqueness checks).
     *
     * [fieldErrors] maps each field name to a list of i18n message keys.
     * [summaryKey] is the i18n key for the top-level summary message (HTTP 422).
     *
     * Example:
     * ```kotlin
     * throw AppException.ValidationErrors(
     *     fieldErrors = mapOf(
     *         "email"    to listOf("error.email.already_registered"),
     *         "username" to listOf("error.username.taken"),
     *     )
     * )
     * ```
     */
    class ValidationErrors(
        val fieldErrors: Map<String, List<String>>,
        summaryKey: String = "error.validation_failed",
    ) : AppException(HttpStatus.UNPROCESSABLE_ENTITY, summaryKey)

    companion object {
        /**
         * Stable, locale-independent RFC 9457 "type" clients can switch on.
         * "error.username.taken" → "/errors/username-taken". Message keys are thereby
         * part of the API contract: renaming one changes the type URI.
         */
        fun typeUri(messageKey: String): URI =
            URI.create("/errors/" + messageKey.removePrefix("error.").replace('.', '-').replace('_', '-'))
    }
}
