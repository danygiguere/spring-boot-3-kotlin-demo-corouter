package com.example.corouterdemo.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

class JwtPropertiesTest {
    @Test
    fun `missing secret fails startup`() {
        assertThatThrownBy { JwtProperties("", "iss", "aud") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("JWT_SECRET")
    }

    @Test
    fun `short secret fails startup`() {
        assertThatThrownBy { JwtProperties("too-short", "iss", "aud") }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `missing audience fails startup`() {
        assertThatThrownBy { JwtProperties("x".repeat(32), "iss", " ") }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `valid secret is accepted with default ttl`() {
        assertThat(JwtProperties("x".repeat(32), "iss", "aud").ttl).isEqualTo(Duration.ofMinutes(15))
    }
}
