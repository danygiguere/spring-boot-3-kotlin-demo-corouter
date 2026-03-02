package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.EnterpriseRequest
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.extension.locale
import com.example.corouterdemo.extension.validateAndThrow
import com.example.corouterdemo.service.EnterpriseService
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class EnterpriseHandler(
    private val enterpriseService: EnterpriseService,
    private val validator: Validator,
) {
    suspend fun create(request: ServerRequest): ServerResponse {
        val enterpriseRequest = validator.validateAndThrow(request.awaitBody<EnterpriseRequest>(), request.locale())
        val enterprise = enterpriseService.create(enterpriseRequest)
        return ServerResponse.status(HttpStatus.CREATED).bodyValueAndAwait(enterprise)
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = request.pathVariable("id").toLong()
        val enterprise = enterpriseService.findById(id)
        return ServerResponse.ok().bodyValueAndAwait(enterprise)
    }

    suspend fun findAll(request: ServerRequest): ServerResponse = ServerResponse.ok().bodyAndAwait(enterpriseService.findAll())

    suspend fun search(request: ServerRequest): ServerResponse {
        val name =
            request.queryParam("name").orElseThrow {
                AppException.BadRequest("error.query.param.required", "name")
            }
        return ServerResponse.ok().bodyAndAwait(enterpriseService.searchByName(name))
    }
}
