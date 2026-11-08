package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.User
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * Tenant-facing queries take the caller's `enterpriseId`: a user of another enterprise behaves exactly like a
 * missing one. R2DBC has no lazy relations, so every relation is an explicit query.
 */
interface UserRepository : CoroutineCrudRepository<User, Long> {
    /** Cross-tenant by design: dev token lookup only (email is UNIQUE). */
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id AND enterprise_id = :enterpriseId")
    suspend fun findInEnterprise(
        id: Long,
        enterpriseId: Long,
    ): User?

    /** hasMany (enterprise → users), oldest first, keyset-paginated. */
    @Query(
        """
        SELECT * FROM users
        WHERE enterprise_id = :enterpriseId AND id > :afterId
        ORDER BY id
        LIMIT :limit
        """,
    )
    fun findPageInEnterprise(
        enterpriseId: Long,
        afterId: Long,
        limit: Int,
    ): Flow<User>

    /** Bulk load by ids: one array parameter, so the statement does not grow with the list. */
    @Query("SELECT * FROM users WHERE id = ANY(:ids) AND enterprise_id = :enterpriseId")
    fun findAllInEnterprise(
        ids: Array<Long>,
        enterpriseId: Long,
    ): Flow<User>

    /** manyToMany — users who like a post, through post_likes. */
    @Query(
        """
        SELECT u.* FROM users u
        JOIN post_likes l ON l.user_id = u.id
        WHERE l.post_id = :postId AND u.enterprise_id = :enterpriseId AND u.id > :afterId
        ORDER BY u.id
        LIMIT :limit
        """,
    )
    fun findLikersInEnterprise(
        postId: Long,
        enterpriseId: Long,
        afterId: Long,
        limit: Int,
    ): Flow<User>
}
