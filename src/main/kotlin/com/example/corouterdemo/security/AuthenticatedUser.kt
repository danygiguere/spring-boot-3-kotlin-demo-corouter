package com.example.corouterdemo.security

import org.springframework.security.oauth2.jwt.Jwt

/**
 * The caller, as described by its VERIFIED JWT — the only trusted source of "who is asking" and "which tenant". A
 * user or enterprise id read from the path, body or a custom header is client input.
 */
data class AuthenticatedUser(
    /** Claim `sub`. */
    val userId: Long,
    /** Claim [ENTERPRISE_ID_CLAIM]: the tenant every query is scoped to. */
    val enterpriseId: Long,
) {
    companion object {
        const val ENTERPRISE_ID_CLAIM = "enterpriseId"

        /** Claim shapes are already checked by [SecurityConfig.claimShapeValidator]. */
        fun from(jwt: Jwt): AuthenticatedUser = AuthenticatedUser(jwt.subject.toLong(), jwt.getClaim<Number>(ENTERPRISE_ID_CLAIM).toLong())
    }
}
