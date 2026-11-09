package com.example.corouterdemo.security

import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.exception.ApplicationExceptionHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.config.web.server.invoke
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import java.time.Instant
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless bearer-token API. This filter chain only AUTHENTICATES; whether the caller may touch a given resource
 * (author) is checked in the service at the point of action.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties::class)
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        exceptionHandler: ApplicationExceptionHandler,
    ): SecurityWebFilterChain {
        // 401/403 go through the same handler as every other error: same RFC 9457 shape, locale and correlationId.
        val unauthorized =
            ServerAuthenticationEntryPoint { exchange, _ -> exceptionHandler.handle(exchange, AppException.Unauthorized("error.401")) }
        val forbidden = ServerAccessDeniedHandler { exchange, _ -> exceptionHandler.handle(exchange, AppException.Forbidden("error.403")) }

        return http {
            // Bearer tokens, no cookies: the browser never attaches credentials on its own, so CSRF does not apply.
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            authorizeExchange {
                PUBLIC_PATHS.forEach { authorize(it, permitAll) }
                authorize(anyExchange, authenticated)
            }
            oauth2ResourceServer {
                jwt { }
                authenticationEntryPoint = unauthorized
                accessDeniedHandler = forbidden
            }
            exceptionHandling {
                authenticationEntryPoint = unauthorized
                accessDeniedHandler = forbidden
            }
        }
    }

    /**
     * Verifies the signature with the pinned algorithm (HS256 only — the token's own `alg` header is not trusted),
     * then `exp` (required), `nbf`, `iss`, `aud`, and the shape of the claims this application reads. Any failure is
     * a 401.
     */
    @Bean
    fun jwtDecoder(properties: JwtProperties): ReactiveJwtDecoder =
        NimbusReactiveJwtDecoder
            .withSecretKey(secretKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtValidators.createDefaultWithIssuer(properties.issuer),
                        JwtClaimValidator<Instant>(JwtClaimNames.EXP) { it != null },
                        JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { it != null && properties.audience in it },
                        claimShapeValidator(),
                    ),
                )
            }

    companion object {
        /** The only routes reachable without a token. Everything else is denied by default. */
        val PUBLIC_PATHS =
            listOf(
                // API docs (disabled unless SWAGGER_ENABLED)
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/webjars/**",
                "/v3/api-docs/**",
                // Dev token endpoint (exists only with DEV_TOKEN_ENABLED)
                "/auth/dev-token",
            )

        private val NUMERIC = Regex("[1-9][0-9]{0,18}")

        /** `sub` is a positive user id: a malformed claim is a 401, not a 500. */
        fun claimShapeValidator(): OAuth2TokenValidator<Jwt> =
            OAuth2TokenValidator { jwt ->
                if (jwt.subject?.let { NUMERIC.matches(it) } == true) {
                    OAuth2TokenValidatorResult.success()
                } else {
                    OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Missing or malformed sub claim", null))
                }
            }

        fun secretKey(properties: JwtProperties): SecretKey = SecretKeySpec(properties.secretBytes, "HmacSHA256")
    }
}
