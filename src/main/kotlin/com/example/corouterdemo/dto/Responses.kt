package com.example.corouterdemo.dto

import com.example.corouterdemo.domain.entity.Enterprise
import com.example.corouterdemo.domain.entity.Image
import com.example.corouterdemo.domain.entity.Post
import com.example.corouterdemo.domain.entity.Profile
import com.example.corouterdemo.domain.entity.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "An enterprise")
data class EnterpriseResponse(
    val id: Long,
    val name: String,
) {
    constructor(enterprise: Enterprise) : this(enterprise.id!!, enterprise.name)
}

@Schema(description = "A colleague, as seen by other users of the same enterprise (no email)")
data class UserResponse(
    val id: Long,
    val name: String,
) {
    constructor(user: User) : this(user.id!!, user.name)
}

@Schema(description = "The authenticated user")
data class MeResponse(
    val id: Long,
    val enterpriseId: Long,
    val name: String,
    val email: String,
) {
    constructor(user: User) : this(user.id!!, user.enterpriseId, user.name, user.email)
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
    val url: String,
    val position: Int,
) {
    constructor(image: Image) : this(image.id!!, image.url, image.position)
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
    constructor(post: Post, author: User, imageCount: Long, likeCount: Long) :
        this(post.id!!, UserResponse(author), post.content, post.createdAt, imageCount, likeCount)
}

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

/** `GROUP BY post_id` row. Posts with no rows are absent: callers treat them as 0. */
data class PostCount(
    val postId: Long,
    val total: Long,
)
