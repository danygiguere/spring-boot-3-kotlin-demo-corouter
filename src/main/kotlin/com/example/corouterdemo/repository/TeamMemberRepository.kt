package com.example.corouterdemo.repository

import com.example.corouterdemo.domain.entity.TeamMember
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface TeamMemberRepository : CoroutineCrudRepository<TeamMember, Long> {
    fun findAllByUserId(userId: Long): Flow<TeamMember>

    fun findAllByTeamId(teamId: Long): Flow<TeamMember>

    fun findAllByTeamIdIn(teamIds: Collection<Long>): Flow<TeamMember>
}
