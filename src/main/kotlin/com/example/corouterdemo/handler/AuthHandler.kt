package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.DevTokenRequest
import com.example.corouterdemo.extension.awaitValidBody
import com.example.corouterdemo.security.DevTokenEnabled
import com.example.corouterdemo.service.DevTokenService
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
@DevTokenEnabled
class AuthHandler(
    private val devTokenService: DevTokenService,
    private val validator: Validator,
) {
    suspend fun devToken(request: ServerRequest): ServerResponse {
        val body = request.awaitValidBody<DevTokenRequest>(validator)
        return ServerResponse.ok().bodyValueAndAwait(devTokenService.issue(body.email!!))
    }
}
