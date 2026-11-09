package com.example.corouterdemo.router

import com.example.corouterdemo.dto.ImageResponse
import com.example.corouterdemo.dto.MeResponse
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.dto.ProfileRequest
import com.example.corouterdemo.dto.ProfileResponse
import com.example.corouterdemo.dto.UserResponse
import com.example.corouterdemo.handler.UserHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class UserRouter(
    private val handler: UserHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/users/me",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "me",
            operation =
                Operation(
                    operationId = "getMe",
                    summary = "Get the authenticated user",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(schema = Schema(implementation = MeResponse::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/me/profile",
            method = [RequestMethod.PUT],
            beanClass = UserHandler::class,
            beanMethod = "replaceMyProfile",
            operation =
                Operation(
                    operationId = "replaceMyProfile",
                    summary = "Create or replace the authenticated user's profile",
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = ProfileRequest::class))]),
                    responses = [
                        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findAll",
            operation =
                Operation(
                    operationId = "getUsers",
                    summary = "List users, oldest first",
                    parameters = [
                        Parameter(`in` = ParameterIn.QUERY, name = "afterId", description = "Cursor: last id of the previous page"),
                        Parameter(`in` = ParameterIn.QUERY, name = "limit", description = "1 to 100"),
                    ],
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(array = ArraySchema(schema = Schema(implementation = UserResponse::class)))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{id}/profile",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findProfile",
            operation =
                Operation(
                    operationId = "getUserProfile",
                    summary = "Get a user's profile (hasOne)",
                    responses = [
                        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = ProfileResponse::class))]),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{id}/posts",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findPosts",
            operation =
                Operation(
                    operationId = "getUserPosts",
                    summary = "List a user's posts, newest first (hasMany)",
                    parameters = [
                        Parameter(`in` = ParameterIn.QUERY, name = "beforeId", description = "Cursor: last id of the previous page"),
                        Parameter(`in` = ParameterIn.QUERY, name = "limit", description = "1 to 100"),
                    ],
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(array = ArraySchema(schema = Schema(implementation = PostSummary::class)))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{id}/liked-posts",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findLikedPosts",
            operation =
                Operation(
                    operationId = "getUserLikedPosts",
                    summary = "List posts a user likes, newest first (manyToMany)",
                    parameters = [
                        Parameter(`in` = ParameterIn.QUERY, name = "beforeId", description = "Cursor: last id of the previous page"),
                        Parameter(`in` = ParameterIn.QUERY, name = "limit", description = "1 to 100"),
                    ],
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(array = ArraySchema(schema = Schema(implementation = PostSummary::class)))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/users/{id}/images",
            method = [RequestMethod.GET],
            beanClass = UserHandler::class,
            beanMethod = "findImages",
            operation =
                Operation(
                    operationId = "getUserImages",
                    summary = "List images of a user's posts, newest first (hasManyThrough)",
                    parameters = [
                        Parameter(`in` = ParameterIn.QUERY, name = "beforeId", description = "Cursor: last id of the previous page"),
                        Parameter(`in` = ParameterIn.QUERY, name = "limit", description = "1 to 100"),
                    ],
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(array = ArraySchema(schema = Schema(implementation = ImageResponse::class)))],
                        ),
                    ],
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
                    summary = "Get a user (no email)",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(schema = Schema(implementation = UserResponse::class))],
                        ),
                    ],
                ),
        ),
    )
    fun userRoutes() =
        coRouter {
            "/users".nest {
                GET("/me", handler::me)
                PUT("/me/profile", handler::replaceMyProfile)
                GET("", handler::findAll)
                GET("/{id}/profile", handler::findProfile)
                GET("/{id}/posts", handler::findPosts)
                GET("/{id}/liked-posts", handler::findLikedPosts)
                GET("/{id}/images", handler::findImages)
                GET("/{id}", handler::findById)
            }
        }
}
