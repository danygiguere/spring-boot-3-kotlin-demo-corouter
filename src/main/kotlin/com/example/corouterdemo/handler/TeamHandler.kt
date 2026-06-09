package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.TeamRequest
import com.example.corouterdemo.extension.locale
import com.example.corouterdemo.extension.validateAndThrow
import com.example.corouterdemo.service.TeamService
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class TeamHandler(
    private val teamService: TeamService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse {
        val teamRequest = validator.validateAndThrow(request.awaitBody<TeamRequest>(), request.locale())
        val team = teamService.create(teamRequest)
        return ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(team)
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val team = teamService.findById(id)
        return ServerResponse.ok().bodyValueAndAwait(team)
    }

    suspend fun findAll(request: ServerRequest): ServerResponse = ServerResponse.ok().bodyAndAwait(teamService.findAll())

    suspend fun findByEnterprise(request: ServerRequest): ServerResponse {
        val enterpriseId = request.pathVariable("enterpriseId").toLong()
        return ServerResponse.ok().bodyAndAwait(teamService.findByEnterprise(enterpriseId))
    }

    suspend fun findSummary(request: ServerRequest): ServerResponse =
        ServerResponse.ok().bodyValueAndAwait(teamService.findTeamsWithEnterpriseInfo())
}
