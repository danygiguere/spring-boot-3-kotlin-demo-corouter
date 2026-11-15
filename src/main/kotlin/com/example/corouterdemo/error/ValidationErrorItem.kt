package com.example.corouterdemo.error

/**
 * One validation failure in a 400 response.
 *
 * The rejected value is left out on purpose. It may be a password, a token, or PII, and the
 * caller already knows what it sent.
 *
 * @property field the request field or parameter name, or null for an object-level error
 * @property code the constraint name (for example `NotBlank`), which clients can match on
 * @property message the message translated into the caller's locale
 */
data class ValidationErrorItem(
    val field: String?,
    val code: String?,
    val message: String,
)
