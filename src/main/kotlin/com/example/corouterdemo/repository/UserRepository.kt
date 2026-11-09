package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.User
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/** R2DBC has no lazy relations, so every relation is an explicit query. */
interface UserRepository : CoroutineCrudRepository<User, Long> {
    /** Dev token lookup only (email is UNIQUE). */
    suspend fun findByEmail(email: String): User?

    /** Oldest first, keyset-paginated. */
    @Query("SELECT * FROM users WHERE id > :afterId ORDER BY id LIMIT :limit")
    fun findPage(
        afterId: Long,
        limit: Int,
    ): Flow<User>

    /** manyToMany — users who like a post, through post_likes. */
    @Query(
        """
        SELECT u.* FROM users u
        JOIN post_likes l ON l.user_id = u.id
        WHERE l.post_id = :postId AND u.id > :afterId
        ORDER BY u.id
        LIMIT :limit
        """,
    )
    fun findLikers(
        postId: Long,
        afterId: Long,
        limit: Int,
    ): Flow<User>
}
