package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A page of a keyset-paginated list")
data class CursorPage<T>(
    val data: List<T>,
    @field:Schema(description = "Send as `cursor` to get the next page; null on the last page", nullable = true)
    val nextCursor: Long?,
) {
    companion object {
        /** [rows] was fetched with `limit + 1`: the extra row only tells that another page exists. */
        fun <T> of(
            rows: List<T>,
            limit: Int,
            id: (T) -> Long,
        ): CursorPage<T> {
            if (rows.size <= limit) return CursorPage(rows, null)
            val data = rows.take(limit)
            return CursorPage(data, id(data.last()))
        }
    }
}
