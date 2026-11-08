package com.example.corouterdemo.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** Custom collection constraint: no element may appear twice. `null` is valid. */
@MustBeDocumented
@Constraint(validatedBy = [NoDuplicatesValidator::class])
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoDuplicates(
    val message: String = "{validation.noDuplicates}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NoDuplicatesValidator : ConstraintValidator<NoDuplicates, Collection<*>?> {
    override fun isValid(
        values: Collection<*>?,
        context: ConstraintValidatorContext,
    ): Boolean = values == null || values.toSet().size == values.size
}
