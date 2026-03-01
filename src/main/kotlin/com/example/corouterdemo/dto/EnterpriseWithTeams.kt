package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Enterprise with its teams and users")
data class EnterpriseWithTeams(
    val id: Long?,
    val name: String,
    val phoneNumber: String?,
    val website: String?,
    val email: String,
    val description: String?,
    val teams: List<TeamWithMembers>,
)
