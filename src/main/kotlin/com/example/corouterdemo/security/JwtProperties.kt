package com.example.corouterdemo.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * JWT settings (`app.jwt.*`). Validated at startup: a missing or short key stops the application instead of
 * producing forgeable tokens.
 */
@ConfigurationProperties("app.jwt")
data class JwtProperties(
    val secret: String?,
    val issuer: String?,
    val audience: String?,
    val ttl: Duration = Duration.ofMinutes(15),
) {
    init {
        check(secret != null && secret.toByteArray().size >= MIN_SECRET_BYTES) {
            "app.jwt.secret (env JWT_SECRET) must be at least $MIN_SECRET_BYTES bytes."
        }
        check(!issuer.isNullOrBlank()) { "app.jwt.issuer must be set." }
        check(!audience.isNullOrBlank()) { "app.jwt.audience must be set." }
    }

    val secretBytes: ByteArray get() = secret!!.toByteArray()

    companion object {
        /** HS256 requires a key of at least 256 bits. */
        const val MIN_SECRET_BYTES = 32
    }
}
