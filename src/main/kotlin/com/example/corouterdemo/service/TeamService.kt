package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.Team
import com.example.corouterdemo.dto.TeamRequest
import com.example.corouterdemo.dto.TeamSummary
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.TeamJooqRepository
import com.example.corouterdemo.repository.TeamRepository
import kotlinx.coroutines.flow.Flow
import org.springframework.stereotype.Service

@Service
class TeamService(
    private val teamRepository: TeamRepository,
    private val teamJooqRepository: TeamJooqRepository,
) {
    suspend fun create(form: TeamRequest): Team {
        val team =
            Team(
                enterpriseId = form.enterpriseId!!,
                name = form.name!!,
                description = form.description,
            )
        return teamRepository.save(team)
    }

    suspend fun findById(id: Long): Team = teamRepository.findById(id) ?: throw AppException.NotFound("error.team.not.found", id)

    fun findAll(): Flow<Team> = teamRepository.findAll()

    fun findByEnterprise(enterpriseId: Long): Flow<Team> = teamRepository.findAllByEnterpriseId(enterpriseId)

    fun findTeamsWithEnterpriseInfo(): Flow<TeamSummary> = teamJooqRepository.findTeamsWithEnterpriseInfo()
}
