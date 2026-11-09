package com.example.corouterdemo.service

import com.example.corouterdemo.dto.TokenResponse
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.UserRepository
import com.example.corouterdemo.security.DevTokenEnabled
import com.example.corouterdemo.security.DevTokenIssuer
import org.springframework.stereotype.Service

/** Passwordless token for local development. Exists only with DEV_TOKEN_ENABLED, which requires a loopback bind. */
@Service
@DevTokenEnabled
class DevTokenService(
    private val userRepository: UserRepository,
    private val issuer: DevTokenIssuer,
) {
    suspend fun issue(email: String): TokenResponse =
        userRepository.findByEmail(email)?.let(issuer::issue)
            ?: throw AppException.Unauthorized("error.auth.invalid")
}
