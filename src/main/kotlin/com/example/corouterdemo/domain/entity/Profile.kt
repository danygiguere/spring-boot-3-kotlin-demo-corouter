package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/** belongsTo [User]; at most one per user (UNIQUE (user_id)). */
@Table("profiles")
data class Profile(
    @Id val id: Long? = null,
    val userId: Long,
    val bio: String?,
    val avatarUrl: String?,
)
