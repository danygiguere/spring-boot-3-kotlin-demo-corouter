package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.User
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "User representation returned to clients")
data class UserResponse(
    val id: Long?,
    val name: String,
    val email: String,
    val phoneNumber: String?,
)

fun User.toResponse() = UserResponse(id = id, name = name, email = email, phoneNumber = phoneNumber)
