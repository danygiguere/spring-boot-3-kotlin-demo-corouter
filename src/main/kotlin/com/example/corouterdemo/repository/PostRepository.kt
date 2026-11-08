package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Post
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * A post is visible to users of its author's enterprise. Every read takes the caller's `enterpriseId`; every write
 * also takes the author's `userId`, so the WHERE clause is the permission check and targets at most one row (primary
 * key).
 */
interface PostRepository : CoroutineCrudRepository<Post, Long> {
    @Query(
        """
        SELECT p.* FROM posts p
        JOIN users u ON u.id = p.user_id
        WHERE p.id = :postId AND u.enterprise_id = :enterpriseId
        """,
    )
    suspend fun findVisible(
        postId: Long,
        enterpriseId: Long,
    ): Post?

    /** hasManyThrough (enterprise → users → posts), newest first, keyset-paginated. */
    @Query(
        """
        SELECT p.* FROM posts p
        JOIN users u ON u.id = p.user_id
        WHERE u.enterprise_id = :enterpriseId AND p.id < :beforeId
        ORDER BY p.id DESC
        LIMIT :limit
        """,
    )
    fun findFeed(
        enterpriseId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<Post>

    /** hasMany (user → posts). */
    @Query(
        """
        SELECT p.* FROM posts p
        JOIN users u ON u.id = p.user_id
        WHERE p.user_id = :userId AND u.enterprise_id = :enterpriseId AND p.id < :beforeId
        ORDER BY p.id DESC
        LIMIT :limit
        """,
    )
    fun findByAuthor(
        userId: Long,
        enterpriseId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<Post>

    /** manyToMany — posts liked by a user, through post_likes. */
    @Query(
        """
        SELECT p.* FROM posts p
        JOIN users u ON u.id = p.user_id
        JOIN post_likes l ON l.post_id = p.id
        WHERE l.user_id = :userId AND u.enterprise_id = :enterpriseId AND p.id < :beforeId
        ORDER BY p.id DESC
        LIMIT :limit
        """,
    )
    fun findLikedBy(
        userId: Long,
        enterpriseId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<Post>

    /** Returns the number of rows updated: 1, or 0 when the post is gone or not the caller's. */
    @Modifying
    @Query("UPDATE posts SET content = :content WHERE id = :id AND user_id = :userId")
    suspend fun updateContent(
        id: Long,
        userId: Long,
        content: String,
    ): Long

    /** Returns the number of rows deleted: 1, or 0 when the post is gone or not the caller's. */
    @Modifying
    @Query("DELETE FROM posts WHERE id = :id AND user_id = :userId")
    suspend fun deleteOwned(
        id: Long,
        userId: Long,
    ): Long
}
