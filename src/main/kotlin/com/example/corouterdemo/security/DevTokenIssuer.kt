package com.example.corouterdemo.security

import com.example.corouterdemo.domain.entity.User
import com.example.corouterdemo.dto.TokenResponse
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/**
 * Signs tokens for local development only. In production, tokens come from the identity provider; this application
 * only verifies them (see [SecurityConfig.jwtDecoder]). Refuses to start unless the server binds to loopback.
 */
@Component
@DevTokenEnabled
class DevTokenIssuer(
    private val properties: JwtProperties,
    private val clock: Clock,
    serverProperties: ServerProperties,
) {
    init {
        check(serverProperties.address?.isLoopbackAddress == true) {
            "DEV_TOKEN_ENABLED requires a loopback SERVER_ADDRESS (127.0.0.1)."
        }
    }

    private val encoder = NimbusJwtEncoder(ImmutableSecret(SecurityConfig.secretKey(properties)))

    fun issue(user: User): TokenResponse {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer)
                .audience(listOf(properties.audience))
                .subject(user.id.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl))
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return TokenResponse(token, "Bearer", properties.ttl.toSeconds())
    }
}
