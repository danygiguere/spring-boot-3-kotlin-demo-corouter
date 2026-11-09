package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Image
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ImageRepository : CoroutineCrudRepository<Image, Long> {
    /** hasMany (post → images), at most 10 rows (CHECK on position). */
    fun findAllByPostIdOrderByPositionAsc(postId: Long): Flow<Image>

    /** hasManyThrough (user → posts → images), newest first, keyset-paginated. */
    @Query(
        """
        SELECT i.* FROM images i
        JOIN posts p ON p.id = i.post_id
        WHERE p.user_id = :userId AND i.id < :beforeId
        ORDER BY i.id DESC
        LIMIT :limit
        """,
    )
    fun findByUser(
        userId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<Image>
}
