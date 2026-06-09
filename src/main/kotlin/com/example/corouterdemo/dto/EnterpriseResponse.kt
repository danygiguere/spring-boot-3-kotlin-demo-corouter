package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.Enterprise
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Enterprise representation returned to clients")
data class EnterpriseResponse(
    val id: Long?,
    val name: String,
    val phoneNumber: String?,
    val website: String?,
    val email: String,
    val description: String?,
)

fun Enterprise.toResponse() =
    EnterpriseResponse(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        website = website,
        email = email,
        description = description,
    )
