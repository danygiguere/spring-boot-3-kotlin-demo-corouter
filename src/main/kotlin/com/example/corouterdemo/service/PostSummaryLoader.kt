package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.Post
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.repository.ImageRepository
import com.example.corouterdemo.repository.PostLikeRepository
import com.example.corouterdemo.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component

/**
 * Turns a page of posts into summaries with a constant number of queries — authors, image counts and like counts are
 * each loaded in bulk (three queries, run concurrently), then joined in memory through hash maps.
 */
@Component
class PostSummaryLoader(
    private val userRepository: UserRepository,
    private val imageRepository: ImageRepository,
    private val postLikeRepository: PostLikeRepository,
) {
    /** Keeps the order of [posts]. The page is already bounded by the caller's limit. */
    suspend fun load(
        posts: Flow<Post>,
        enterpriseId: Long,
    ): List<PostSummary> {
        val page = posts.toList()
        if (page.isEmpty()) return emptyList()
        val postIds = page.map { it.id!! }.toTypedArray()
        val authorIds = page.map { it.userId }.distinct().toTypedArray()

        return coroutineScope {
            val authors = async { userRepository.findAllInEnterprise(authorIds, enterpriseId).toList().associateBy { it.id!! } }
            val imageCounts = async { imageRepository.countByPostIds(postIds).toList().associate { it.postId to it.total } }
            val likeCounts = async { postLikeRepository.countByPostIds(postIds).toList().associate { it.postId to it.total } }
            val authorsById = authors.await()
            val images = imageCounts.await()
            val likes = likeCounts.await()
            // An author deleted between the two queries takes their posts with them (ON DELETE CASCADE).
            page.mapNotNull { post ->
                authorsById[post.userId]?.let { author ->
                    PostSummary(post, author, images[post.id] ?: 0, likes[post.id] ?: 0)
                }
            }
        }
    }
}
