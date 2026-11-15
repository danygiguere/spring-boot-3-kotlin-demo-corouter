package com.example.corouterdemo.error

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolation
import jakarta.validation.ElementKind
import jakarta.validation.MessageInterpolator
import jakarta.validation.ValidationException
import jakarta.validation.ValidatorFactory
import jakarta.validation.metadata.ConstraintDescriptor
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.validation.method.ParameterErrors
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import java.util.Locale

private val logger = KotlinLogging.logger {}

/**
 * Turns every kind of validation failure into the same list of [ValidationErrorItem], translated
 * into the request locale.
 *
 * Why this is needed in WebFlux: Bean Validation translates its messages with
 * `LocaleContextHolder`, which WebFlux never sets, so the messages it produces are in the JVM
 * locale. Each message is therefore translated again from the original constraint violation, in
 * the locale of the request (see [localize]).
 *
 * Rejected values are never included, because they may be passwords or personal data.
 */
@Component
class ValidationErrorTranslator(
    private val messages: MessageSource,
    private val translator: MessageTranslator,
    private val validatorFactory: ValidatorFactory,
) {
    /** `@Valid @RequestBody` failed. */
    fun fromBinding(
        ex: WebExchangeBindException,
        locale: Locale,
    ): List<ValidationErrorItem> =
        ex.bindingResult.allErrors.map { error ->
            ValidationErrorItem(field = (error as? FieldError)?.field, code = error.code, message = resolve(error, locale))
        }

    /**
     * Built-in method validation failed: constraints placed directly on `@RequestParam`,
     * `@PathVariable`, `@RequestHeader`, or on a `@Valid @RequestBody` of the same method.
     */
    fun fromMethodValidation(
        ex: HandlerMethodValidationException,
        locale: Locale,
    ): List<ValidationErrorItem> =
        ex.parameterValidationResults.flatMap { result ->
            if (result is ParameterErrors) {
                result.fieldErrors.map { ValidationErrorItem(it.field, it.code, resolve(it, locale)) }
            } else {
                val name = apiName(result.methodParameter)
                result.resolvableErrors.map { error ->
                    ValidationErrorItem(name, error.codes?.lastOrNull(), resolve(result, error, locale))
                }
            }
        }

    /**
     * `Validator.validate` or a `@Validated` bean failed. For method validation only the leaf of the
     * property path is shown: the full path (`createUser.arg0.email`) would reveal method names.
     */
    fun fromViolations(
        violations: Set<ConstraintViolation<*>>,
        locale: Locale,
    ): List<ValidationErrorItem> =
        violations.map { violation ->
            ValidationErrorItem(
                field = fieldOf(violation),
                code = violation.constraintDescriptor.annotation.annotationClass.simpleName,
                message = localize(violation, locale),
            )
        }

    /** Field errors raised by application code with [FieldValidationException]. */
    fun fromFieldViolations(
        violations: List<FieldViolation>,
        locale: Locale,
    ): List<ValidationErrorItem> =
        violations.map { violation ->
            ValidationErrorItem(
                field = violation.field,
                code = violation.code,
                message =
                    translator.translate(violation.messageKey, violation.publicArgs, locale)
                        ?: translator.invalidValue(locale),
            )
        }

    private fun resolve(
        error: ObjectError,
        locale: Locale,
    ): String {
        val violation = runCatching { error.unwrap(ConstraintViolation::class.java) }.getOrNull()
        return if (violation != null) localize(violation, locale) else resolveCodes(error, locale)
    }

    private fun resolve(
        result: ParameterValidationResult,
        error: MessageSourceResolvable,
        locale: Locale,
    ): String {
        val violation = runCatching { result.unwrap(error, ConstraintViolation::class.java) }.getOrNull()
        return if (violation != null) localize(violation, locale) else resolveCodes(error, locale)
    }

    /**
     * Translates the constraint's message template (for example `{post.content.required}`) in the
     * request locale. The template is looked up in `messages*.properties` first, then in Hibernate
     * Validator's bundled messages.
     */
    private fun localize(
        violation: ConstraintViolation<*>,
        locale: Locale,
    ): String =
        try {
            validatorFactory.messageInterpolator.interpolate(violation.messageTemplate, ViolationContext(violation), locale)
        } catch (ex: RuntimeException) {
            logger.warn(ex) { "Could not interpolate validation template=${violation.messageTemplate} locale=$locale" }
            translator.invalidValue(locale)
        }

    /**
     * Fallback for errors that do not come from Bean Validation. Tries the codes from most to least
     * specific: for example `NotBlank.signUpRequest.email`, then `NotBlank.email`, then `NotBlank`.
     */
    private fun resolveCodes(
        error: MessageSourceResolvable,
        locale: Locale,
    ): String =
        try {
            messages.getMessage(error, locale)
        } catch (_: NoSuchMessageException) {
            logger.warn { "Missing translation for validation codes=${error.codes?.toList()} locale=$locale" }
            translator.invalidValue(locale)
        }

    private class ViolationContext(
        private val violation: ConstraintViolation<*>,
    ) : MessageInterpolator.Context {
        override fun getConstraintDescriptor(): ConstraintDescriptor<*> = violation.constraintDescriptor

        override fun getValidatedValue(): Any? = violation.invalidValue

        override fun <T> unwrap(type: Class<T>): T =
            if (type.isInstance(this)) type.cast(this) else throw ValidationException("Cannot unwrap to $type")
    }

    private companion object {
        const val LIST_ELEMENT_NODE = ".<list element>"

        /** `imageUrls[0]` for a bean, the leaf property for method validation. */
        fun fieldOf(violation: ConstraintViolation<*>): String? {
            val nodes = violation.propertyPath.toList()
            if (nodes.any { it.kind == ElementKind.METHOD || it.kind == ElementKind.CONSTRUCTOR }) {
                return nodes.lastOrNull { it.kind == ElementKind.PROPERTY }?.name
            }
            return violation.propertyPath
                .toString()
                .replace(LIST_ELEMENT_NODE, "")
                .ifEmpty { null }
        }
    }
}

/** The name the API exposes for a parameter: the annotation's explicit name, else the Kotlin name. */
internal fun apiName(parameter: MethodParameter?): String? {
    parameter ?: return null
    val explicit =
        parameter.getParameterAnnotation(PathVariable::class.java)?.name
            ?: parameter.getParameterAnnotation(RequestParam::class.java)?.name
            ?: parameter.getParameterAnnotation(RequestHeader::class.java)?.name
    return explicit?.takeIf { it.isNotEmpty() } ?: parameter.parameterName
}
