package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/** belongsTo [Post]. [position] (1 to 10) is the display order. */
@Table("images")
data class Image(
    @Id val id: Long? = null,
    val postId: Long,
    val url: String,
    val position: Int,
)
