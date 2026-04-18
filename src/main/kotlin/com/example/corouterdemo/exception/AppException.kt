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

    /** Multiple field-level validation errors (HTTP 422). fieldErrors maps field → list of message keys. */
    class ValidationErrors(
        val fieldErrors: Map<String, List<String>>,
        summaryKey: String = "error.validation_failed",
    ) : AppException(summaryKey, emptyArray(), null, HttpStatus.UNPROCESSABLE_ENTITY)
}
