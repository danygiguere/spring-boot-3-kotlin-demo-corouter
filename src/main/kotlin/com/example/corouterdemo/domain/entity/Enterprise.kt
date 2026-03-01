package com.example.corouterdemo.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("enterprises")
data class Enterprise(
    @Id val id: Long? = null,
    val name: String,
    val phoneNumber: String? = null,
    val website: String? = null,
    val email: String,
    val description: String? = null,
)
