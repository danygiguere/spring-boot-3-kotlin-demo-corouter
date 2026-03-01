package com.example.corouterdemo.dto

data class TeamWithMembers(
    val id: Long?,
    val name: String,
    val description: String?,
    val userIds: List<Long>,
)
