package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.Team
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface TeamRepository : CoroutineCrudRepository<Team, Long> {
    fun findAllByEnterpriseId(enterpriseId: Long): Flow<Team>

    fun findAllByEnterpriseIdIn(enterpriseIds: Collection<Long>): Flow<Team>
}
