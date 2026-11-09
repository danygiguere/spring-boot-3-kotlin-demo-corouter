package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Post
import com.example.corouterdemo.dto.PostSummaryRow
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

/**
 * Columns of [PostSummaryRow], selected from a page `p` of posts. The page is limited first, so the counts run for at
 * most `limit` posts (indexed by UNIQUE (post_id, …)).
 */
private const val SUMMARY_COLUMNS = """
    SELECT p.id, p.user_id, u.name AS author_name, p.content, p.created_at,
           (SELECT COUNT(*) FROM images i WHERE i.post_id = p.id) AS image_count,
           (SELECT COUNT(*) FROM post_likes l WHERE l.post_id = p.id) AS like_count
"""

/** Joins the author and keeps the page order. */
private const val SUMMARY_JOIN = """
    JOIN users u ON u.id = p.user_id
    ORDER BY p.id DESC
"""

/**
 * A post is visible to every user and writable by its author: every write takes the author's `userId`, so the WHERE
 * clause is the permission check and targets at most one row (primary key). List queries return a page of summaries
 * in one statement, newest first, keyset-paginated.
 */
interface PostRepository : CoroutineCrudRepository<Post, Long> {
    /** All posts. */
    @Query(
        """
        $SUMMARY_COLUMNS
        FROM (SELECT * FROM posts WHERE id < :beforeId ORDER BY id DESC LIMIT :limit) p
        $SUMMARY_JOIN
        """,
    )
    fun findFeed(
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummaryRow>

    /** hasMany (user → posts). */
    @Query(
        """
        $SUMMARY_COLUMNS
        FROM (SELECT * FROM posts WHERE user_id = :userId AND id < :beforeId ORDER BY id DESC LIMIT :limit) p
        $SUMMARY_JOIN
        """,
    )
    fun findByAuthor(
        userId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummaryRow>

    /** manyToMany — posts liked by a user, through post_likes. */
    @Query(
        """
        $SUMMARY_COLUMNS
        FROM (
            SELECT q.* FROM posts q
            JOIN post_likes pl ON pl.post_id = q.id
            WHERE pl.user_id = :userId AND q.id < :beforeId
            ORDER BY q.id DESC
            LIMIT :limit
        ) p
        $SUMMARY_JOIN
        """,
    )
    fun findLikedBy(
        userId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummaryRow>

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
