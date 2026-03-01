package com.example.corouterdemo.extension

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.i18n.SimpleLocaleContext
import org.springframework.web.reactive.function.server.ServerRequest
import java.util.Locale

fun ServerRequest.locale(): Locale {
    val range = headers().acceptLanguage().firstOrNull()?.range ?: return Locale.ENGLISH
    return Locale.forLanguageTag(range)
}

fun <T : Any> Validator.validateAndThrow(
    target: T,
    locale: Locale,
    vararg groups: Class<*>,
): T {
    LocaleContextHolder.setLocaleContext(SimpleLocaleContext(locale))
    try {
        val violations = if (groups.isEmpty()) validate(target) else validate(target, *groups)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        return target
    } finally {
        LocaleContextHolder.resetLocaleContext()
    }
}
