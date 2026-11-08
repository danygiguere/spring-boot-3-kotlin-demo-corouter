package com.example.corouterdemo.validation

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Plain unit tests: no Spring context, no database. */
class CustomValidatorsTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    data class Sample(
        @field:SafeText val text: String?,
        @field:NoDuplicates val items: List<String>?,
    )

    private fun valid(
        text: String?,
        items: List<String>?,
    ) = validator.validate(Sample(text, items)).isEmpty()

    @Test
    fun `safeText accepts plain text and null`() {
        assertThat(valid("Hello, 1 < 2 and 3 > 2!\nSecond line\tTabbed", null)).isTrue()
        assertThat(valid(null, null)).isTrue()
    }

    @Test
    fun `safeText rejects html and control characters`() {
        assertThat(valid("<script>alert(1)</script>", null)).isFalse()
        assertThat(valid("hello <b>world</b>", null)).isFalse()
        assertThat(valid("<!-- comment -->", null)).isFalse()
        assertThat(valid("null\u0000byte", null)).isFalse()
    }

    @Test
    fun `noDuplicates rejects repeated elements`() {
        assertThat(valid("ok", listOf("a", "b"))).isTrue()
        assertThat(valid("ok", emptyList())).isTrue()
        assertThat(valid("ok", listOf("a", "b", "a"))).isFalse()
    }

    @Test
    fun `violations carry the message templates`() {
        val templates = validator.validate(Sample("<b>", listOf("a", "a"))).map { it.messageTemplate }
        assertThat(templates).containsExactlyInAnyOrder("{validation.safeText}", "{validation.noDuplicates}")
    }
}
