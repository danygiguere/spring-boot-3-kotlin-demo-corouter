package com.example.corouterdemo.router

import com.example.corouterdemo.dto.CreatePostRequest
import com.example.corouterdemo.dto.ImageResponse
import com.example.corouterdemo.dto.PostDetails
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.dto.UpdatePostRequest
import com.example.corouterdemo.dto.UserResponse
import com.example.corouterdemo.handler.PostHandler
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

/** Posts of the caller's enterprise; writes are restricted to the author. */
@Configuration
class PostRouter(
    private val handler: PostHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/posts",
            method = [RequestMethod.GET],
            beanClass = PostHandler::class,
            beanMethod = "feed",
            operation =
                Operation(
                    operationId = "getFeed",
                    summary = "The enterprise feed, newest first (hasManyThrough)",
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
            path = "/posts",
            method = [RequestMethod.POST],
            beanClass = PostHandler::class,
            beanMethod = "create",
            operation =
                Operation(
                    operationId = "createPost",
                    summary = "Publish a post with its images, as the authenticated user",
                    description =
                        "A retry with the same Idempotency-Key and body returns the post created the first time " +
                            "(header Idempotent-Replayed: true).",
                    parameters = [
                        Parameter(
                            `in` = ParameterIn.HEADER,
                            name = PostHandler.IDEMPOTENCY_KEY_HEADER,
                            required = true,
                            description = "8 to 128 characters: letters, digits, '-' or '_'",
                            example = "3f8a2c4e-9b1d-4e6f-a5c7-2d9e8b1f0a3c",
                        ),
                    ],
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = CreatePostRequest::class))]),
                    responses = [
                        ApiResponse(responseCode = "201", content = [Content(schema = Schema(implementation = PostDetails::class))]),
                        ApiResponse(responseCode = "409", description = "Idempotency-Key already used with a different body"),
                    ],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}/images",
            method = [RequestMethod.GET],
            beanClass = PostHandler::class,
            beanMethod = "findImages",
            operation =
                Operation(
                    operationId = "getPostImages",
                    summary = "List a post's images in display order (hasMany)",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(array = ArraySchema(schema = Schema(implementation = ImageResponse::class)))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}/likes",
            method = [RequestMethod.GET],
            beanClass = PostHandler::class,
            beanMethod = "findLikers",
            operation =
                Operation(
                    operationId = "getPostLikers",
                    summary = "List users who like a post (manyToMany)",
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
            path = "/posts/{id}/likes",
            method = [RequestMethod.PUT],
            beanClass = PostHandler::class,
            beanMethod = "like",
            operation =
                Operation(
                    operationId = "likePost",
                    summary = "Like a post as the authenticated user (idempotent)",
                    responses = [ApiResponse(responseCode = "204", description = "Liked")],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}/likes",
            method = [RequestMethod.DELETE],
            beanClass = PostHandler::class,
            beanMethod = "unlike",
            operation =
                Operation(
                    operationId = "unlikePost",
                    summary = "Remove the authenticated user's like (idempotent)",
                    responses = [ApiResponse(responseCode = "204", description = "Not liked")],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}",
            method = [RequestMethod.GET],
            beanClass = PostHandler::class,
            beanMethod = "findById",
            operation =
                Operation(
                    operationId = "getPostById",
                    summary = "Get a post with its author, images and like count",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(schema = Schema(implementation = PostDetails::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}",
            method = [RequestMethod.PUT],
            beanClass = PostHandler::class,
            beanMethod = "update",
            operation =
                Operation(
                    operationId = "updatePost",
                    summary = "Edit a post's content (author only)",
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = UpdatePostRequest::class))]),
                    responses = [
                        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = PostDetails::class))]),
                        ApiResponse(responseCode = "403", description = "Not the author"),
                    ],
                ),
        ),
        RouterOperation(
            path = "/posts/{id}",
            method = [RequestMethod.DELETE],
            beanClass = PostHandler::class,
            beanMethod = "delete",
            operation =
                Operation(
                    operationId = "deletePost",
                    summary = "Delete a post and its images (author only)",
                    responses = [
                        ApiResponse(responseCode = "204", description = "Deleted"),
                        ApiResponse(responseCode = "403", description = "Not the author"),
                    ],
                ),
        ),
    )
    fun postRoutes() =
        coRouter {
            "/posts".nest {
                GET("", handler::feed)
                POST("", handler::create)
                GET("/{id}/images", handler::findImages)
                GET("/{id}/likes", handler::findLikers)
                PUT("/{id}/likes", handler::like)
                DELETE("/{id}/likes", handler::unlike)
                GET("/{id}", handler::findById)
                PUT("/{id}", handler::update)
                DELETE("/{id}", handler::delete)
            }
        }
}
