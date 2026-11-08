package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

/** The tenant. hasMany [User]; hasManyThrough [Post] (through users). */
@Table("enterprises")
data class Enterprise(
    @Id val id: Long? = null,
    val name: String,
)
