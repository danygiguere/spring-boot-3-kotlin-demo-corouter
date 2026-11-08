package com.example.corouterdemo.router

import com.example.corouterdemo.dto.EnterpriseResponse
import com.example.corouterdemo.handler.EnterpriseHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class EnterpriseRouter(
    private val handler: EnterpriseHandler,
) {
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/enterprises/current",
            method = [RequestMethod.GET],
            beanClass = EnterpriseHandler::class,
            beanMethod = "current",
            operation =
                Operation(
                    operationId = "getCurrentEnterprise",
                    summary = "Get the caller's enterprise",
                    responses = [
                        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = EnterpriseResponse::class))]),
                    ],
                ),
        ),
    )
    fun enterpriseRoutes() =
        coRouter {
            GET("/enterprises/current", handler::current)
        }
}
