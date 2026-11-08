package com.example.corouterdemo.handler

import com.example.corouterdemo.security.CurrentUser
import com.example.corouterdemo.service.EnterpriseService
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class EnterpriseHandler(
    private val enterpriseService: EnterpriseService,
    private val currentUser: CurrentUser,
) {
    suspend fun current(request: ServerRequest): ServerResponse =
        ServerResponse.ok().bodyValueAndAwait(enterpriseService.current(currentUser.get()))
}
