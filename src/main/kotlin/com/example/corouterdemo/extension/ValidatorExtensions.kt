package com.example.corouterdemo.extension

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.i18n.SimpleLocaleContext
import java.util.Locale

/** Synchronous: the locale is set and reset around the call, on the same thread (no suspension point). */
fun <T : Any> Validator.validateAndThrow(
    target: T,
    locale: Locale,
): T {
    LocaleContextHolder.setLocaleContext(SimpleLocaleContext(locale))
    try {
        val violations = validate(target)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        return target
    } finally {
        LocaleContextHolder.resetLocaleContext()
    }
}
