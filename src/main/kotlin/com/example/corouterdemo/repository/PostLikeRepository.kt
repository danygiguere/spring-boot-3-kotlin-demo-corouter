package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.PostLike
import com.example.corouterdemo.dto.PostCount
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface PostLikeRepository : CoroutineCrudRepository<PostLike, Long> {
    suspend fun countByPostId(postId: Long): Long

    /** One aggregate for a whole page of posts, instead of one COUNT per post. */
    @Query("SELECT post_id, COUNT(*) AS total FROM post_likes WHERE post_id = ANY(:postIds) GROUP BY post_id")
    fun countByPostIds(postIds: Array<Long>): Flow<PostCount>

    /** Idempotent: liking twice leaves one row, enforced by UNIQUE (post_id, user_id). */
    @Modifying
    @Query(
        """
        INSERT INTO post_likes (post_id, user_id)
        VALUES (:postId, :userId)
        ON CONFLICT (post_id, user_id) DO NOTHING
        """,
    )
    suspend fun likeIfAbsent(
        postId: Long,
        userId: Long,
    ): Long

    /** Idempotent; targets at most one row (UNIQUE (post_id, user_id)). */
    @Modifying
    @Query("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    suspend fun unlike(
        postId: Long,
        userId: Long,
    ): Long
}
