package com.example.corouterdemo.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * Custom field constraint: plain text only. Rejects HTML tags and control characters (newlines and tabs are fine).
 * `null` is valid, so combine with `@NotBlank` when the field is required.
 */
@MustBeDocumented
@Constraint(validatedBy = [SafeTextValidator::class])
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class SafeText(
    val message: String = "{validation.safeText}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class SafeTextValidator : ConstraintValidator<SafeText, CharSequence?> {
    override fun isValid(
        value: CharSequence?,
        context: ConstraintValidatorContext,
    ): Boolean = value == null || (!HTML_TAG.containsMatchIn(value) && value.none(::isForbiddenControl))

    private companion object {
        /** An opening or closing tag, a comment or a doctype: `<b>`, `</p>`, `<!--`. */
        val HTML_TAG = Regex("<[a-zA-Z/!?]")

        fun isForbiddenControl(c: Char) = c.isISOControl() && c != '\n' && c != '\r' && c != '\t'
    }
}
