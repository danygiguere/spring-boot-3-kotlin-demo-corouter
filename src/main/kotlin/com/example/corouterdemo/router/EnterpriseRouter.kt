package com.example.corouterdemo.router

import com.example.corouterdemo.domain.entity.Enterprise
import com.example.corouterdemo.dto.EnterpriseRequest
import com.example.corouterdemo.dto.EnterpriseWithTeams
import com.example.corouterdemo.handler.EnterpriseHandler
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
class EnterpriseRouter(
    private val handler: EnterpriseHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/enterprises",
            method = [RequestMethod.POST],
            beanClass = EnterpriseHandler::class,
            beanMethod = "create",
            operation =
                Operation(
                    operationId = "createEnterprise",
                    summary = "Create an enterprise",
                    requestBody = RequestBody(content = [Content(schema = Schema(implementation = EnterpriseRequest::class))]),
                    responses = [
                        ApiResponse(
                            responseCode = "201",
                            content = [Content(schema = Schema(implementation = Enterprise::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/enterprises/search",
            method = [RequestMethod.GET],
            beanClass = EnterpriseHandler::class,
            beanMethod = "search",
            operation =
                Operation(
                    operationId = "searchEnterprises",
                    summary = "Search enterprises by name",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = Enterprise::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/enterprises/{id}",
            method = [RequestMethod.GET],
            beanClass = EnterpriseHandler::class,
            beanMethod = "findById",
            operation =
                Operation(
                    operationId = "getEnterpriseById",
                    summary = "Get an enterprise by ID",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [Content(schema = Schema(implementation = EnterpriseWithTeams::class))],
                        ),
                    ],
                ),
        ),
        RouterOperation(
            path = "/enterprises",
            method = [RequestMethod.GET],
            beanClass = EnterpriseHandler::class,
            beanMethod = "findAll",
            operation =
                Operation(
                    operationId = "getAllEnterprises",
                    summary = "Get all enterprises",
                    responses = [
                        ApiResponse(
                            responseCode = "200",
                            content = [
                                Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = ArraySchema(schema = Schema(implementation = Enterprise::class)),
                                ),
                            ],
                        ),
                    ],
                ),
        ),
    )
    fun enterpriseRoutes() =
        coRouter {
            "/enterprises".nest {
                POST("", handler::create)
                GET("/search", handler::search)
                GET("/{id}", handler::findById)
                GET("", handler::findAll)
            }
        }
}
