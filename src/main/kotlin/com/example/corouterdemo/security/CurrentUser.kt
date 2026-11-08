package com.example.corouterdemo.security

import com.example.corouterdemo.exception.AppException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/** The authenticated caller. The security context travels in the Reactor context, which coRouter bridges. */
@Component
class CurrentUser {
    suspend fun get(): AuthenticatedUser {
        val token =
            ReactiveSecurityContextHolder.getContext().awaitSingleOrNull()?.authentication as? JwtAuthenticationToken
                ?: throw AppException.Unauthorized("error.401")
        return AuthenticatedUser.from(token.token)
    }
}
