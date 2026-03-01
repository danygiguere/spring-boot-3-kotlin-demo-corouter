package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("teams")
data class Team(
    @Id val id: Long? = null,
    val enterpriseId: Long,
    val name: String,
    val description: String? = null,
)
