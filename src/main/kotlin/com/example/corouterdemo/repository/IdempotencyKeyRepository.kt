package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.IdempotencyKey
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

interface IdempotencyKeyRepository : CoroutineCrudRepository<IdempotencyKey, Long> {
    /**
     * Claims the key atomically and returns its row id; null when the key was already claimed. A concurrent claim of
     * the same key waits on the UNIQUE index until the first transaction ends, then sees the committed row.
     */
    @Query(
        """
        INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at)
        VALUES (:userId, :key, :requestHash, :createdAt)
        ON CONFLICT (user_id, idempotency_key) DO NOTHING
        RETURNING id
        """,
    )
    suspend fun claim(
        userId: Long,
        key: String,
        requestHash: String,
        createdAt: Instant,
    ): Long?

    suspend fun findByUserIdAndIdempotencyKey(
        userId: Long,
        idempotencyKey: String,
    ): IdempotencyKey?

    @Modifying
    @Query("UPDATE idempotency_keys SET post_id = :postId WHERE id = :id")
    suspend fun attachPost(
        id: Long,
        postId: Long,
    ): Long
}
