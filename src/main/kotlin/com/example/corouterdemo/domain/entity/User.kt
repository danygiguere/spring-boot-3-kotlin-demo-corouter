package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/**
 * belongsTo [Enterprise]; hasOne [Profile]; hasMany [Post]; manyToMany [Post] through [PostLike]. Never serialized
 * directly: responses use DTOs that list their fields explicitly.
 */
@Table("users")
data class User(
    @Id val id: Long? = null,
    val enterpriseId: Long,
    val name: String,
    val email: String,
)
