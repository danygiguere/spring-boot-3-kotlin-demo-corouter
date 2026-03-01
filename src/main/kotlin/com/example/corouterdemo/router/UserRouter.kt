package com.example.corouterdemo.router

import com.example.corouterdemo.domain.entity.Team
import com.example.corouterdemo.domain.entity.TeamMember
import com.example.corouterdemo.domain.entity.User
import com.example.corouterdemo.dto.UserRequest
import com.example.corouterdemo.handler.UserHandler
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
class UserRouter(
    private val handler: UserHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/users",
            method = [RequestMethod.POST],
            beanClass = UserHandler::class,
            beanMethod = "create",
            operation =
                Operation(
                    operationId = "createUser",
                    summary = "Create a user",
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = UserRequest::class))]),
                    responses = [ApiResponse(responseCode = "201", content = [Content(schema = Schema(implementation = User::class))])],
                ),
        ),
        RouterOperation(
            path = "/users/{id}",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findById",
            operation =
                Operation(
                    operationId = "getUserById",
                    summary = "Get a user by ID",
                    responses = [ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = User::class))])],
                ),
        ),
        RouterOperation(
            path = "/users",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findAll",
            operation =
                Operation(
                    operationId = "getAllUsers",
                    summary = "Get all users",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = User::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{userId}/teams/{teamId}",
            method = [RequestMethod.POST],
            beanClass = UserHandler::class,
            beanMethod = "assignToTeam",
            operation =
                Operation(
                    operationId = "assignUserToTeam",
                    summary = "Assign a user to a team",
                    responses = [
                        ApiResponse(
                            responseCode = "201",
                            content = [Content(schema = Schema(implementation = TeamMember::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{userId}/teams",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findTeams",
            operation =
                Operation(
                    operationId = "getUserTeams",
                    summary = "Get teams for a user",
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
    fun userRoutes() =
        coRouter {
            "/users".nest {
                POST("", handler::create)
                GET("/{userId}/teams", handler::findTeams)
                GET("/{id}", handler::findById)
                GET("", handler::findAll)
                POST("/{userId}/teams/{teamId}", handler::assignToTeam)
            }
        }
}
