package com.example.corouterdemo.error

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * Looks up public messages in `messages*.properties`.
 *
 * A missing key returns null instead of the key itself, so callers fall back to a generic message
 * and a raw key never reaches the client. The gap is logged so it gets fixed.
 */
@Component
class MessageTranslator(
    private val messages: MessageSource,
) {
    fun translate(
        key: String,
        args: List<Any>,
        locale: Locale,
    ): String? {
        val text = messages.getMessage(key, args.toTypedArray(), null, locale)
        if (text == null) logger.warn { "Missing translation key=$key locale=$locale" }
        return text
    }

    /** The message used when a validation error has no translation of its own. */
    fun invalidValue(locale: Locale): String = translate("error.validation.invalidValue", emptyList(), locale) ?: "Invalid value."
}
