package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("team_members")
data class TeamMember(
    @Id val id: Long? = null,
    val teamId: Long,
    val userId: Long,
)
