package com.example.corouterdemo.router

import com.example.corouterdemo.domain.entity.Team
import com.example.corouterdemo.dto.TeamRequest
import com.example.corouterdemo.dto.TeamSummary
import com.example.corouterdemo.dto.TeamWithMembers
import com.example.corouterdemo.handler.TeamHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class TeamRouter(
    private val handler: TeamHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/teams",
            method = [RequestMethod.POST],
            beanClass = TeamHandler::class,
            beanMethod = "create",
            operation =
                Operation(
                    operationId = "createTeam",
                    summary = "Create a team",
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = TeamRequest::class))]),
                    responses = [ApiResponse(responseCode = "201", content = [Content(schema = Schema(implementation = Team::class))])],
                ),
        ),
        RouterOperation(
            path = "/teams/summary",
            method = [RequestMethod.GET],
            beanClass = TeamHandler::class,
            beanMethod = "findSummary",
            operation =
                Operation(
                    operationId = "getTeamsSummary",
                    summary = "Get teams with enterprise info",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = TeamSummary::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/teams/enterprise/{enterpriseId}",
            method = [RequestMethod.GET],
            beanClass = TeamHandler::class,
            beanMethod = "findByEnterprise",
            operation =
                Operation(
                    operationId = "getTeamsByEnterprise",
                    summary = "Get teams by enterprise ID",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = Team::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/teams/{id}",
            method = [RequestMethod.GET],
            beanClass = TeamHandler::class,
            beanMethod = "findById",
            operation =
                Operation(
                    operationId = "getTeamById",
                    summary = "Get a team by ID",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(schema = Schema(implementation = TeamWithMembers::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/teams",
            method = [RequestMethod.GET],
            beanClass = TeamHandler::class,
            beanMethod = "findAll",
            operation =
                Operation(
                    operationId = "getAllTeams",
                    summary = "Get all teams",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = Team::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
    )
    fun teamRoutes() =
        coRouter {
            "/teams".nest {
                POST("", handler::create)
                GET("/summary", handler::findSummary)
                GET("/enterprise/{enterpriseId}", handler::findByEnterprise)
                GET("/{id}", handler::findById)
                GET("", handler::findAll)
            }
        }
}
