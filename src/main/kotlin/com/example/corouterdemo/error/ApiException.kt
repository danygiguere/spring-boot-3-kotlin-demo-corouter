package com.example.corouterdemo.error

import org.springframework.http.HttpStatus

/**
 * Base class for every error the application throws on purpose.
 *
 * Each exception carries two separate messages:
 *
 * - **Public message**: [messageKey] plus [publicArgs]. The handler looks the key up in
 *   `messages*.properties` for the caller's locale. This text is the only one sent to the client,
 *   so the key must always have a translation.
 * - **Internal message**: `internalMessage`, which becomes [Throwable.message]. It is written to the
 *   server logs and never sent to the client. It may contain identifiers, SQL fragments, upstream
 *   errors, and similar details. It is not translated, because only operators read it.
 *
 * Rules for callers:
 * - Put in [publicArgs] only values that are safe to show the caller, such as limits or names
 *   the caller already supplied. Never put secrets, tokens, other users' data, or internal IDs in them.
 * - Keep the original exception as `cause` when you wrap one, so the stack trace stays in the logs.
 * - When a resource exists but belongs to someone else, throw [NotFoundException], not
 *   [ForbiddenException]. A 403 would confirm that the resource exists.
 */
abstract class ApiException(
    val status: HttpStatus,
    val messageKey: String,
    val publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException(internalMessage ?: messageKey, cause)

/** 400: the request is invalid in a way bean validation cannot express. */
class BadRequestException(
    messageKey: String = "error.badRequest",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.BAD_REQUEST, messageKey, publicArgs, internalMessage, cause)

/** 401: credentials are missing or invalid. */
class UnauthenticatedException(
    messageKey: String = "error.unauthenticated",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.UNAUTHORIZED, messageKey, publicArgs, internalMessage, cause)

/** 403: the caller is authenticated but lacks permission for an action, such as an admin-only one. */
class ForbiddenException(
    messageKey: String = "error.forbidden",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.FORBIDDEN, messageKey, publicArgs, internalMessage, cause)

/** 404: the resource does not exist, or the caller is not allowed to know that it exists. */
class NotFoundException(
    messageKey: String = "error.notFound",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.NOT_FOUND, messageKey, publicArgs, internalMessage, cause)

/** 409: a duplicate, an optimistic-lock failure, or a state conflict. */
class ConflictException(
    messageKey: String = "error.conflict",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.CONFLICT, messageKey, publicArgs, internalMessage, cause)

/** 422: the request is well formed but breaks a business rule. */
class BusinessRuleException(
    messageKey: String = "error.businessRule",
    publicArgs: List<Any> = emptyList(),
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.UNPROCESSABLE_ENTITY, messageKey, publicArgs, internalMessage, cause)

/** 503: a dependency is unavailable. The client always gets a generic message. */
class ServiceUnavailableException(
    messageKey: String = "error.serviceUnavailable",
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.SERVICE_UNAVAILABLE, messageKey, emptyList(), internalMessage, cause)

/**
 * 400 with field errors, for rules Bean Validation cannot express: cross-field rules, uniqueness
 * checks against the database, and so on. The response uses the same `errors` format as a Bean
 * Validation failure, so clients handle both the same way.
 *
 * ```kotlin
 * throw FieldValidationException(
 *     FieldViolation("password", "error.user.passwordContainsEmail"),
 *     internalMessage = "password contains the email local part",
 * )
 * ```
 */
class FieldValidationException(
    val violations: List<FieldViolation>,
    internalMessage: String? = null,
    cause: Throwable? = null,
) : ApiException(HttpStatus.BAD_REQUEST, "error.validation", emptyList(), internalMessage, cause) {
    constructor(vararg violations: FieldViolation, internalMessage: String? = null) :
        this(violations.toList(), internalMessage)

    init {
        require(violations.isNotEmpty()) { "FieldValidationException needs at least one violation" }
    }
}

/**
 * One field error raised by application code.
 *
 * @property field the request field, or null for an error on the whole object
 * @property messageKey the public message key, translated like any other
 * @property publicArgs arguments for the message. The same rules as [ApiException.publicArgs] apply.
 * @property code the stable identifier clients can match on. Defaults to the message key.
 */
data class FieldViolation(
    val field: String?,
    val messageKey: String,
    val publicArgs: List<Any> = emptyList(),
    val code: String = messageKey,
)
