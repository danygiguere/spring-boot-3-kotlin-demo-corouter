package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Profile
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ProfileRepository : CoroutineCrudRepository<Profile, Long> {
    /** hasOne — a single nullable result is safe because of UNIQUE (user_id). */
    suspend fun findByUserId(userId: Long): Profile?

    /** Create or replace in one atomic statement: no check-then-insert race, idempotent. */
    @Query(
        """
        INSERT INTO profiles (user_id, bio, avatar_url)
        VALUES (:userId, :bio, :avatarUrl)
        ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, avatar_url = EXCLUDED.avatar_url
        RETURNING *
        """,
    )
    suspend fun upsert(
        userId: Long,
        bio: String?,
        avatarUrl: String?,
    ): Profile
}
