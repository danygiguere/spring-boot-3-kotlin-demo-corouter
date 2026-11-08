package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/** belongsTo [User] (author); hasMany [Image]. [createdAt] is UTC (TIMESTAMPTZ). */
@Table("posts")
data class Post(
    @Id val id: Long? = null,
    val userId: Long,
    val content: String,
    val createdAt: Instant,
)
