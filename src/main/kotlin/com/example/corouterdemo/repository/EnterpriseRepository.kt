package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Enterprise
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface EnterpriseRepository : CoroutineCrudRepository<Enterprise, Long> {
    fun findByNameContainingIgnoreCase(name: String): Flow<Enterprise>
}
