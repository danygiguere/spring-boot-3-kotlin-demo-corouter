package com.example.corouterdemo.exception

import org.springframework.http.HttpStatus

sealed class AppException(
    val messageKey: String,
    val messageArgs: Array<out Any> = emptyArray(),
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

    open class BadRequest(
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
}
