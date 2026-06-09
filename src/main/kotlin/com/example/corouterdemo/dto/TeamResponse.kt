package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.Team
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Team representation returned to clients")
data class TeamResponse(
    val id: Long?,
    val enterpriseId: Long,
    val name: String,
    val description: String?,
)

fun Team.toResponse() = TeamResponse(id = id, enterpriseId = enterpriseId, name = name, description = description)
