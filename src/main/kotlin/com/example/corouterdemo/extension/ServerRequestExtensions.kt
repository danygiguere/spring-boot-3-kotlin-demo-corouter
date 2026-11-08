package com.example.corouterdemo.extension

import com.example.corouterdemo.exception.AppException
import jakarta.validation.Validator
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.awaitBodyOrNull
import java.util.Locale

/** Typed, bounded reads of path and query parameters. Invalid values are a 400, never a 500. */

const val DEFAULT_LIMIT = 20
const val MAX_LIMIT = 100

/** The exchange locale from `LocaleContextResolver` — the same one the error handler uses. */
fun ServerRequest.locale(): Locale = exchange().localeContext.locale ?: Locale.ENGLISH

/** A positive numeric path variable (an entity id). */
fun ServerRequest.pathId(name: String): Long =
    pathVariable(name).toLongOrNull()?.takeIf { it > 0 }
        ?: throw AppException.BadRequest("error.path.param.invalid", arrayOf(name))

/** Page size: [DEFAULT_LIMIT] by default, never more than [MAX_LIMIT]. */
fun ServerRequest.limit(): Int = queryLong("limit", DEFAULT_LIMIT.toLong(), 1, MAX_LIMIT.toLong()).toInt()

/** Keyset cursor for newest-first lists: only ids strictly below it are returned. */
fun ServerRequest.beforeId(): Long = queryLong("beforeId", Long.MAX_VALUE, 1, Long.MAX_VALUE)

/** Keyset cursor for oldest-first lists: only ids strictly above it are returned. */
fun ServerRequest.afterId(): Long = queryLong("afterId", 0, 0, Long.MAX_VALUE)

private fun ServerRequest.queryLong(
    name: String,
    default: Long,
    min: Long,
    max: Long,
): Long {
    val raw = queryParam(name).orElse(null) ?: return default
    return raw.toLongOrNull()?.takeIf { it in min..max }
        ?: throw AppException.BadRequest("error.query.param.invalid", arrayOf(name, min, max))
}

/** The decoded, validated body; a 400 when it is missing. coRouter has no `@Valid`: this is its counterpart. */
suspend inline fun <reified T : Any> ServerRequest.awaitValidBody(validator: Validator): T {
    val body = awaitBodyOrNull<T>() ?: throw AppException.BadRequest("error.body.required")
    return validator.validateAndThrow(body, locale())
}
