package com.example.corouterdemo.router

import com.example.corouterdemo.dto.ImageResponse
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.dto.UserResponse
import io.swagger.v3.oas.annotations.media.Schema

/** OpenAPI descriptions of the paged list responses; router functions carry no generic return types. */
object PageDocs {
    const val CURSOR = "`nextCursor` of the previous page; omit for the first page. Null `nextCursor` means the last page."
    const val LIMIT = "Page size, 1 to 100"
    private const val NEXT_CURSOR = "Send as `cursor` to get the next page; null on the last page"
    private const val PAGE = "A page of a keyset-paginated list"

    @Schema(name = "CursorPageUserResponse", description = PAGE)
    data class Users(
        val data: List<UserResponse>,
        @field:Schema(description = NEXT_CURSOR) val nextCursor: Long?,
    )

    @Schema(name = "CursorPagePostSummary", description = PAGE)
    data class Posts(
        val data: List<PostSummary>,
        @field:Schema(description = NEXT_CURSOR) val nextCursor: Long?,
    )

    @Schema(name = "CursorPageImageResponse", description = PAGE)
    data class Images(
        val data: List<ImageResponse>,
        @field:Schema(description = NEXT_CURSOR) val nextCursor: Long?,
    )
}
