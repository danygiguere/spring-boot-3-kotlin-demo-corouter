package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.Image
import com.example.corouterdemo.domain.entity.Post
import com.example.corouterdemo.domain.entity.Profile
import com.example.corouterdemo.domain.entity.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "A user, as seen by other users (no email)")
data class UserResponse(
    val id: Long,
    val name: String,
) {
    constructor(user: User) : this(user.id!!, user.name)
}

@Schema(description = "The authenticated user")
data class MeResponse(
    val id: Long,
    val name: String,
    val email: String,
) {
    constructor(user: User) : this(user.id!!, user.name, user.email)
}

@Schema(description = "A user's profile")
data class ProfileResponse(
    val userId: Long,
    val bio: String?,
    val avatarUrl: String?,
) {
    constructor(profile: Profile) : this(profile.userId, profile.bio, profile.avatarUrl)
}

@Schema(description = "An image of a post")
data class ImageResponse(
    val id: Long,
    val postId: Long,
    val url: String,
    val position: Int,
) {
    constructor(image: Image) : this(image.id!!, image.postId, image.url, image.position)
}

@Schema(description = "A post in a list, with its author and counts")
data class PostSummary(
    val id: Long,
    val author: UserResponse,
    val content: String,
    val createdAt: Instant,
    val imageCount: Long,
    val likeCount: Long,
) {
    constructor(row: PostSummaryRow) :
        this(row.id, UserResponse(row.userId, row.authorName), row.content, row.createdAt, row.imageCount, row.likeCount)
}

/** Flat row of the post summary queries: the post, its author's name and both counts. */
data class PostSummaryRow(
    val id: Long,
    val userId: Long,
    val authorName: String,
    val content: String,
    val createdAt: Instant,
    val imageCount: Long,
    val likeCount: Long,
)

@Schema(description = "A post with its author, images and like count")
data class PostDetails(
    val id: Long,
    val author: UserResponse,
    val content: String,
    val createdAt: Instant,
    val images: List<ImageResponse>,
    val likeCount: Long,
) {
    constructor(post: Post, author: User, images: List<Image>, likeCount: Long) :
        this(post.id!!, UserResponse(author), post.content, post.createdAt, images.map(::ImageResponse), likeCount)
}

@Schema(description = "Access token, sent as Authorization: Bearer <accessToken>")
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
