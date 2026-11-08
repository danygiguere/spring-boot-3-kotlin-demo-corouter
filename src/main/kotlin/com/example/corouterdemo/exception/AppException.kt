package com.example.corouterdemo.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.ErrorResponseException
import java.net.URI

/**
 * Client-safe error. [messageKey] is resolved via `MessageSource` into the problem "detail" and also feeds the
 * stable "type" URI (see [typeUri]). The cause, when given, is logged but never sent to the client.
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
    ) : AppException(HttpStatus.CONFLICT, messageKey, args)

    class BadRequest(
        messageKey: String,
        args: Array<Any> = emptyArray(),
    ) : AppException(HttpStatus.BAD_REQUEST, messageKey, args)

    class Unauthorized(
        messageKey: String,
    ) : AppException(HttpStatus.UNAUTHORIZED, messageKey)

    class Forbidden(
        messageKey: String,
        args: Array<Any> = emptyArray(),
    ) : AppException(HttpStatus.FORBIDDEN, messageKey, args)

    /**
     * Field errors detected by business logic that Bean Validation cannot express (cross-field rules, uniqueness
     * checks). Maps each field to a list of message keys.
     */
    class ValidationErrors(
        val fieldErrors: Map<String, List<String>>,
    ) : AppException(HttpStatus.UNPROCESSABLE_ENTITY, "error.validation_failed")

    companion object {
        /**
         * Stable, locale-independent RFC 9457 "type" clients can switch on. `error.user.not.found` →
         * `/errors/user-not-found`. Message keys are thereby part of the API contract.
         */
        fun typeUri(messageKey: String): URI =
            URI.create("/errors/" + messageKey.removePrefix("error.").replace('.', '-').replace('_', '-'))
    }
}
