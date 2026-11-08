package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

/** The author is the authenticated user, never a request field. */
@Schema(description = "New post, with up to 10 images in display order")
data class CreatePostRequest(
    @field:NotBlank(message = "{post.content.required}")
    @field:Size(max = 5000, message = "{post.content.size}")
    @Schema(example = "Hello from the coRouter demo!")
    val content: String?,
    @field:Size(max = 10, message = "{post.imageUrls.size}")
    @ArraySchema(schema = Schema(example = "https://images.example.com/hello.jpg"))
    val imageUrls: List<
        @NotBlank(message = "{post.imageUrl.required}")
        @URL(protocol = "https", message = "{post.imageUrl.url}")
        @Size(max = 500, message = "{post.imageUrl.size}")
        String,
    > = emptyList(),
)

@Schema(description = "Post update: only the content is writable")
data class UpdatePostRequest(
    @field:NotBlank(message = "{post.content.required}")
    @field:Size(max = 5000, message = "{post.content.size}")
    @Schema(example = "Edited content")
    val content: String?,
)

/** Replaces the caller's profile: absent fields are cleared. */
@Schema(description = "Profile of the authenticated user")
data class ProfileRequest(
    @field:Size(max = 500, message = "{profile.bio.size}")
    @Schema(example = "Backend developer")
    val bio: String? = null,
    @field:URL(protocol = "https", message = "{profile.avatarUrl.url}")
    @field:Size(max = 500, message = "{profile.avatarUrl.size}")
    @Schema(example = "https://images.example.com/avatars/me.png")
    val avatarUrl: String? = null,
)

@Schema(description = "Dev token request (dev profile only)")
data class DevTokenRequest(
    @field:NotBlank(message = "{auth.email.required}")
    @field:Email(message = "{auth.email.invalid}")
    @field:Size(max = 255, message = "{auth.email.size}")
    @Schema(example = "alice@example.com")
    val email: String?,
)
