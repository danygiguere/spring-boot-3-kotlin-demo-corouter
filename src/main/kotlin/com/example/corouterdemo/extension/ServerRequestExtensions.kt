package com.example.corouterdemo.extension

import com.example.corouterdemo.exception.AppException
import org.springframework.web.reactive.function.server.ServerRequest

fun ServerRequest.pathVariableAsLong(name: String): Long =
    pathVariable(name).toLongOrNull() ?: throw AppException.BadRequest("error.path.param.invalid", arrayOf(name))
