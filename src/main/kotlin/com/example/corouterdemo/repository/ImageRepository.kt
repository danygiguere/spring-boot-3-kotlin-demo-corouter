package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Image
import com.example.corouterdemo.dto.PostCount
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ImageRepository : CoroutineCrudRepository<Image, Long> {
    /** hasMany (post → images), at most 10 rows (CHECK on position). */
    fun findAllByPostIdOrderByPositionAsc(postId: Long): Flow<Image>

    /** One aggregate for a whole page of posts, instead of one COUNT per post. */
    @Query("SELECT post_id, COUNT(*) AS total FROM images WHERE post_id = ANY(:postIds) GROUP BY post_id")
    fun countByPostIds(postIds: Array<Long>): Flow<PostCount>
}
