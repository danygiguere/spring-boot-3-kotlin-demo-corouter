package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.UserRequest
import com.example.corouterdemo.extension.locale
import com.example.corouterdemo.extension.validateAndThrow
import com.example.corouterdemo.service.UserService
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class UserHandler(
    private val userService: UserService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse {
        val userRequest = validator.validateAndThrow(request.awaitBody<UserRequest>(), request.locale())
        val user = userService.create(userRequest)
        return ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(user)
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val user = userService.findById(id)
        return ServerResponse.ok().bodyValueAndAwait(user)
    }

    suspend fun findAll(request: ServerRequest): ServerResponse = ServerResponse.ok().bodyAndAwait(userService.findAll())

    suspend fun assignToTeam(request: ServerRequest): ServerResponse {
        val userId = request.pathVariable("userId").toLong()
        val teamId = request.pathVariable("teamId").toLong()
        val teamMember = userService.assignToTeam(userId, teamId)
        return ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(teamMember)
    }

    suspend fun findTeams(request: ServerRequest): ServerResponse {
        val userId = request.pathVariable("userId").toLong()
        return ServerResponse.ok().bodyAndAwait(userService.findTeamsForUser(userId))
    }
}
