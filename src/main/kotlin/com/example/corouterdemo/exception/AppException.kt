package com.example.corouterdemo.exception

sealed class AppException(
    val messageKey: String,
    val messageArgs: Array<out Any> = emptyArray(),
) : RuntimeException(messageKey) {
    class NotFound(
        messageKey: String,
        vararg args: Any,
    ) : AppException(messageKey, args)

    class Conflict(
        messageKey: String,
        vararg args: Any,
    ) : AppException(messageKey, args)

    open class BadRequest(
        messageKey: String,
        vararg args: Any,
    ) : AppException(messageKey, args)

    class Unauthorized(
        messageKey: String,
        vararg args: Any,
    ) : AppException(messageKey, args)

    class Forbidden(
        messageKey: String,
        vararg args: Any,
    ) : AppException(messageKey, args)
}
