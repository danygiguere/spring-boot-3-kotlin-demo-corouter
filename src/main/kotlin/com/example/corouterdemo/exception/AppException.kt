package com.example.corouterdemo.exception

import org.springframework.http.HttpStatus

sealed class AppException(
    val messageKey: String,
    val args: Array<Any> = emptyArray(),
    cause: Throwable? = null,
    val httpStatus: HttpStatus,
) : RuntimeException(messageKey, cause) {
    class NotFound(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause, HttpStatus.NOT_FOUND)

    class Conflict(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause, HttpStatus.CONFLICT)

    class BadRequest(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause, HttpStatus.BAD_REQUEST)

    class Unauthorized(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause, HttpStatus.UNAUTHORIZED)

    class Forbidden(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause, HttpStatus.FORBIDDEN)

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
    ) : AppException(summaryKey, emptyArray(), null, HttpStatus.UNPROCESSABLE_ENTITY)
}
