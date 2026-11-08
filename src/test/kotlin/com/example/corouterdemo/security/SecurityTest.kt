package com.example.corouterdemo.security

import com.example.corouterdemo.support.IntegrationTest
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.util.Base64

class SecurityTest : IntegrationTest() {
    @Autowired
    lateinit var properties: JwtProperties

    @Test
    fun `missing token - returns 401 problem with correlationId`() {
        webTestClient
            .get()
            .uri("/users/me")
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectHeader()
            .exists("X-Correlation-Id")
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(401)
            .jsonPath("$.correlationId")
            .isNotEmpty
    }

    @Test
    fun `valid token - is accepted`() = expectStatus(signed {}, 200)

    @Test
    fun `token signed with another key - returns 401`() =
        expectStatus(sign("another-key-that-is-at-least-32-bytes-long!", baseClaims().build()), 401)

    @Test
    fun `token for another audience - returns 401`() = expectStatus(signed { audience(listOf("another-api")) }, 401)

    @Test
    fun `token from another issuer - returns 401`() = expectStatus(signed { issuer("someone-else") }, 401)

    @Test
    fun `expired token - returns 401`() =
        expectStatus(signed { issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)) }, 401)

    @Test
    fun `token without expiry - returns 401`() {
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.issuer)
                .audience(listOf(properties.audience))
                .subject("1")
                .claim(AuthenticatedUser.ENTERPRISE_ID_CLAIM, 1)
                .build()
        expectStatus(sign(properties.secret!!, claims), 401)
    }

    @Test
    fun `token with malformed subject - returns 401, not 500`() = expectStatus(signed { subject("alice") }, 401)

    @Test
    fun `unsigned alg none token - returns 401`() {
        val base64 = Base64.getUrlEncoder().withoutPadding()
        val header = base64.encodeToString("""{"alg":"none"}""".toByteArray())
        val exp = Instant.now().plusSeconds(600).epochSecond
        val payload =
            base64.encodeToString(
                """{"sub":"1","enterpriseId":1,"iss":"${properties.issuer}","aud":"${properties.audience}","exp":$exp}""".toByteArray(),
            )
        expectStatus("$header.$payload.", 401)
    }

    @Test
    fun `dev token - unknown email returns 401 without revealing why`() {
        webTestClient
            .post()
            .uri("/auth/dev-token")
            .bodyValue(mapOf("email" to "nobody@example.com"))
            .exchange()
            .expectStatus()
            .isUnauthorized
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/auth-invalid")
    }

    @Test
    fun `responses carry security headers`() {
        asUser(ALICE)
            .get()
            .uri("/users/me")
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .valueEquals("X-Content-Type-Options", "nosniff")
            .expectHeader()
            .exists("Cache-Control")
    }

    private fun expectStatus(
        token: String,
        status: Int,
    ) {
        webTestClient
            .get()
            .uri("/users/me")
            .header("Authorization", "Bearer $token")
            .exchange()
            .expectStatus()
            .isEqualTo(status)
    }

    private fun baseClaims(): JwtClaimsSet.Builder {
        val now = Instant.now()
        return JwtClaimsSet
            .builder()
            .issuer(properties.issuer)
            .audience(listOf(properties.audience))
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .claim(AuthenticatedUser.ENTERPRISE_ID_CLAIM, 1)
    }

    private fun signed(customizer: JwtClaimsSet.Builder.() -> Unit): String =
        sign(properties.secret!!, baseClaims().apply(customizer).build())

    private fun sign(
        secret: String,
        claims: JwtClaimsSet,
    ): String {
        val encoder = NimbusJwtEncoder(ImmutableSecret(secret.toByteArray()))
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
}
