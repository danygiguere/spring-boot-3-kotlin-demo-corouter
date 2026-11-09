package com.example.corouterdemo.security

import org.springframework.security.oauth2.jwt.Jwt

/**
 * The caller, as described by its VERIFIED JWT — the only trusted source of "who is asking". A user id read from the
 * path, body or a custom header is client input.
 */
data class AuthenticatedUser(
    /** Claim `sub`. */
    val userId: Long,
) {
    companion object {
        /** The claim shape is already checked by [SecurityConfig.claimShapeValidator]. */
        fun from(jwt: Jwt): AuthenticatedUser = AuthenticatedUser(jwt.subject.toLong())
    }
}
