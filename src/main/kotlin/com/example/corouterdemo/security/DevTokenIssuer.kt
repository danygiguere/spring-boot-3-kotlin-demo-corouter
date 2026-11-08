package com.example.corouterdemo.security

import com.example.corouterdemo.domain.entity.User
import com.example.corouterdemo.dto.TokenResponse
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.context.annotation.Profile
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
 * only verifies them (see [SecurityConfig.jwtDecoder]).
 */
@Component
@Profile("dev")
class DevTokenIssuer(
    private val properties: JwtProperties,
    private val clock: Clock,
) {
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
                .claim(AuthenticatedUser.ENTERPRISE_ID_CLAIM, user.enterpriseId)
                .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return TokenResponse(token, "Bearer", properties.ttl.toSeconds())
    }
}
