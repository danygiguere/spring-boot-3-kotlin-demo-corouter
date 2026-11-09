package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.IdempotencyKey
import com.example.corouterdemo.domain.entity.Image
import com.example.corouterdemo.domain.entity.Post
import com.example.corouterdemo.dto.CreatePostRequest
import com.example.corouterdemo.dto.ImageResponse
import com.example.corouterdemo.dto.PostDetails
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.dto.UpdatePostRequest
import com.example.corouterdemo.dto.UserResponse
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.IdempotencyKeyRepository
import com.example.corouterdemo.repository.ImageRepository
import com.example.corouterdemo.repository.PostLikeRepository
import com.example.corouterdemo.repository.PostRepository
import com.example.corouterdemo.repository.UserRepository
import com.example.corouterdemo.security.AuthenticatedUser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit

/** Outcome of POST /posts; [replayed] when the Idempotency-Key had already created this post. */
data class CreatedPost(
    val post: PostDetails,
    val replayed: Boolean,
)

/**
 * Posts are visible to every user and writable by the author only. The author check happens here, at the point of
 * action, from the verified token — never from ids in the request body.
 */
@Service
class PostService(
    private val postRepository: PostRepository,
    private val imageRepository: ImageRepository,
    private val postLikeRepository: PostLikeRepository,
    private val userRepository: UserRepository,
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val userService: UserService,
    private val transactionalOperator: TransactionalOperator,
    private val clock: Clock,
) {
    // --- Reads --------------------------------------------------------------------------------------------------

    fun feed(
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummary> = postRepository.findFeed(beforeId, limit).map(::PostSummary)

    /** The existence check runs before the [Flow] is returned, so a 404 is raised before any byte is written. */
    suspend fun findByAuthor(
        userId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummary> {
        userService.requireUser(userId)
        return postRepository.findByAuthor(userId, beforeId, limit).map(::PostSummary)
    }

    suspend fun findLikedBy(
        userId: Long,
        beforeId: Long,
        limit: Int,
    ): Flow<PostSummary> {
        userService.requireUser(userId)
        return postRepository.findLikedBy(userId, beforeId, limit).map(::PostSummary)
    }

    /** The post, its author, images and like count: three independent queries run concurrently. */
    suspend fun findById(id: Long): PostDetails {
        val post = requirePost(id)
        return coroutineScope {
            val author = async { userService.requireUser(post.userId) }
            val images = async { imageRepository.findAllByPostIdOrderByPositionAsc(id).toList() }
            val likeCount = async { postLikeRepository.countByPostId(id) }
            PostDetails(post, author.await(), images.await(), likeCount.await())
        }
    }

    /** The visibility check runs before the [Flow] is returned, so a 404 is raised before any byte is written. */
    suspend fun findImages(id: Long): Flow<ImageResponse> {
        requirePost(id)
        return imageRepository.findAllByPostIdOrderByPositionAsc(id).map(::ImageResponse)
    }

    suspend fun findLikers(
        id: Long,
        afterId: Long,
        limit: Int,
    ): Flow<UserResponse> {
        requirePost(id)
        return userRepository.findLikers(id, afterId, limit).map(::UserResponse)
    }

    // --- Writes -------------------------------------------------------------------------------------------------

    /**
     * Creates the post and its images in ONE transaction, together with the claim on [idempotencyKey]. A retry with
     * the same key and body returns the post created the first time; the same key with another body is a 409.
     */
    suspend fun create(
        me: AuthenticatedUser,
        idempotencyKey: String,
        request: CreatePostRequest,
    ): CreatedPost {
        val requestHash = hash(request)
        val now = clock.instant().truncatedTo(ChronoUnit.MICROS) // PostgreSQL precision

        val claim: Claim =
            transactionalOperator.executeAndAwait {
                val keyId = idempotencyKeyRepository.claim(me.userId, idempotencyKey, requestHash, now)
                if (keyId != null) {
                    val details = insertPost(me, request, now)
                    idempotencyKeyRepository.attachPost(keyId, details.id)
                    Claim.Inserted(details)
                } else {
                    val existing =
                        idempotencyKeyRepository.findByUserIdAndIdempotencyKey(me.userId, idempotencyKey)
                            ?: error("Idempotency key conflict without a row")
                    Claim.AlreadyCreated(replayedPostId(existing, requestHash))
                }
            }

        // The replayed post is read after the transaction ends: its concurrent queries need their own connections.
        return when (claim) {
            is Claim.Inserted -> CreatedPost(claim.post, replayed = false)
            is Claim.AlreadyCreated -> CreatedPost(findById(claim.postId), replayed = true)
        }
    }

    /** Only the author may edit. Setting the content to an absolute value is naturally idempotent. */
    suspend fun update(
        me: AuthenticatedUser,
        id: Long,
        request: UpdatePostRequest,
    ): PostDetails {
        requireAuthor(me, id)
        if (postRepository.updateContent(id, me.userId, request.content!!) != 1L) throw postNotFound(id)
        return findById(id)
    }

    /** Only the author may delete. Images and likes go with the post (ON DELETE CASCADE). */
    suspend fun delete(
        me: AuthenticatedUser,
        id: Long,
    ) {
        requireAuthor(me, id)
        if (postRepository.deleteOwned(id, me.userId) != 1L) throw postNotFound(id)
    }

    /** Idempotent: liking twice keeps one like. */
    suspend fun like(
        me: AuthenticatedUser,
        id: Long,
    ) {
        requirePost(id)
        try {
            postLikeRepository.likeIfAbsent(id, me.userId)
        } catch (e: DataIntegrityViolationException) {
            // The post was deleted between the existence check and the insert (foreign key violation).
            throw AppException.NotFound("error.post.not.found", arrayOf(id), e)
        }
    }

    /** Idempotent: unliking a post that is not liked changes nothing. */
    suspend fun unlike(
        me: AuthenticatedUser,
        id: Long,
    ) {
        requirePost(id)
        postLikeRepository.unlike(id, me.userId)
    }

    // --- Helpers ------------------------------------------------------------------------------------------------

    private suspend fun insertPost(
        me: AuthenticatedUser,
        request: CreatePostRequest,
        now: java.time.Instant,
    ): PostDetails {
        val author = userService.requireUser(me.userId)
        val post = postRepository.save(Post(userId = author.id!!, content = request.content!!, createdAt = now))
        val images =
            imageRepository
                .saveAll(request.imageUrls.mapIndexed { i, url -> Image(postId = post.id!!, url = url, position = i + 1) })
                .toList()
        return PostDetails(post, author, images, 0)
    }

    private suspend fun requirePost(id: Long): Post = postRepository.findById(id) ?: throw postNotFound(id)

    /** Not authored by the caller is a 403: every post is visible, so its existence is no secret. */
    private suspend fun requireAuthor(
        me: AuthenticatedUser,
        id: Long,
    ): Post =
        requirePost(id).also {
            if (it.userId != me.userId) throw AppException.Forbidden("error.post.not.author", arrayOf(id))
        }

    /** What the transaction of [create] did with the Idempotency-Key. */
    private sealed interface Claim {
        data class Inserted(
            val post: PostDetails,
        ) : Claim

        data class AlreadyCreated(
            val postId: Long,
        ) : Claim
    }

    private companion object {
        fun postNotFound(id: Long) = AppException.NotFound("error.post.not.found", arrayOf(id))

        fun replayedPostId(
            existing: IdempotencyKey,
            requestHash: String,
        ): Long {
            if (existing.requestHash != requestHash) throw AppException.Conflict("error.idempotency.key.reused")
            return existing.postId ?: throw AppException.Conflict("error.idempotency.key.gone")
        }

        /** Length-prefixed fields, so two different requests can never produce the same input. */
        fun hash(request: CreatePostRequest): String {
            val canonical =
                buildString {
                    (listOf(request.content!!) + request.imageUrls).forEach { append(it.length).append(':').append(it) }
                }
            return MessageDigest
                .getInstance("SHA-256")
                .digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }
}
