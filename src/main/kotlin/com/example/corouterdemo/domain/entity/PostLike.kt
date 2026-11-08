package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/** Join row of the [User] ↔ [Post] manyToMany; UNIQUE (post_id, user_id). */
@Table("post_likes")
data class PostLike(
    @Id val id: Long? = null,
    val postId: Long,
    val userId: Long,
    val createdAt: Instant,
)
