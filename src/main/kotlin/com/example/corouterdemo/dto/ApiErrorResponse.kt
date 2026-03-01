package com.example.corouterdemo.dto

data class ApiErrorResponse(
    val message: String,
    val code: String,
    val correlationId: String,
    val errors: Map<String, List<String>>? = null,
)
