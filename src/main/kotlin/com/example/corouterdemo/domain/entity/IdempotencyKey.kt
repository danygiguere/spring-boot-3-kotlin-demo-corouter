package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

/** A client Idempotency-Key already used by [userId], with the post it created. */
@Table("idempotency_keys")
data class IdempotencyKey(
    @Id val id: Long? = null,
    val userId: Long,
    val idempotencyKey: String,
    val requestHash: String,
    val postId: Long?,
    val createdAt: Instant,
)
