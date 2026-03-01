package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Team with enterprise info (JOOQ join result)")
data class TeamSummary(
    val teamId: Long?,
    val teamName: String,
    val teamDescription: String?,
    val enterpriseName: String,
    val enterpriseEmail: String,
)
