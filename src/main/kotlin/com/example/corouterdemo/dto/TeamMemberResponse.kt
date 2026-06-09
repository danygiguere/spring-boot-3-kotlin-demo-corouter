package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.TeamMember
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Team membership representation returned to clients")
data class TeamMemberResponse(
    val id: Long?,
    val teamId: Long,
    val userId: Long,
)

fun TeamMember.toResponse() = TeamMemberResponse(id = id, teamId = teamId, userId = userId)
