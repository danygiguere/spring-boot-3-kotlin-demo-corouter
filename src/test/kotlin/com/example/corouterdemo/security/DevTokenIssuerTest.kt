package com.example.corouterdemo.security

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.web.ServerProperties
import java.net.InetAddress
import java.time.Clock

class DevTokenIssuerTest {
    private val properties = JwtProperties("x".repeat(32), "iss", "aud")

    private fun issuer(address: String?) =
        DevTokenIssuer(properties, Clock.systemUTC(), ServerProperties().apply { this.address = address?.let(InetAddress::getByName) })

    @Test
    fun `all interfaces bind fails startup`() {
        assertThatThrownBy { issuer(null) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("SERVER_ADDRESS")
    }

    @Test
    fun `non-loopback bind fails startup`() {
        assertThatThrownBy { issuer("0.0.0.0") }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `loopback bind is accepted`() {
        assertThatCode { issuer("127.0.0.1") }.doesNotThrowAnyException()
    }
}
