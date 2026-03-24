package com.example.corouterdemo.exception

sealed class AppException(
    val messageKey: String,
    val messageArgs: Array<out Any> = emptyArray(),
    cause: Throwable? = null,
) : RuntimeException(messageKey, cause) {
    class NotFound(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause)

    class Conflict(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause)

    open class BadRequest(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause)

    class Unauthorized(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause)

    class Forbidden(
        messageKey: String,
        args: Array<Any> = emptyArray(),
        cause: Throwable? = null,
    ) : AppException(messageKey, args, cause)
}
