package com.example.corouterdemo.service

import com.example.corouterdemo.dto.TokenResponse
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.UserRepository
import com.example.corouterdemo.security.DevTokenIssuer
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/** Passwordless token for local development. Exists only in the dev profile, which binds to loopback. */
@Service
@Profile("dev")
class DevTokenService(
    private val userRepository: UserRepository,
    private val issuer: DevTokenIssuer,
) {
    suspend fun issue(email: String): TokenResponse =
        userRepository.findByEmail(email)?.let(issuer::issue)
            ?: throw AppException.Unauthorized("error.auth.invalid")
}
