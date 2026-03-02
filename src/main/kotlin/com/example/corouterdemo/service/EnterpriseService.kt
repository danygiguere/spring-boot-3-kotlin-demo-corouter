package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.Enterprise
import com.example.corouterdemo.dto.EnterpriseRequest
import com.example.corouterdemo.dto.EnterpriseWithTeams
import com.example.corouterdemo.dto.TeamWithMembers
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.EnterpriseRepository
import com.example.corouterdemo.repository.TeamMemberRepository
import com.example.corouterdemo.repository.TeamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class EnterpriseService(
    private val enterpriseRepository: EnterpriseRepository,
    private val teamRepository: TeamRepository,
    private val teamMemberRepository: TeamMemberRepository,
) {
    suspend fun create(form: EnterpriseRequest): Enterprise {
        val enterprise =
            Enterprise(
                name = form.name!!,
                phoneNumber = form.phoneNumber,
                website = form.website,
                email = form.email!!,
                description = form.description,
            )
        return enterpriseRepository.save(enterprise)
    }

    suspend fun findById(id: Long): Enterprise =
        enterpriseRepository.findById(id) ?: throw AppException.NotFound("error.enterprise.not.found", id)

    fun findAll(): Flow<Enterprise> = enterpriseRepository.findAll()

    fun searchByName(name: String): Flow<EnterpriseWithTeams> =
        enterpriseRepository.findByNameContainingIgnoreCase(name).map { enterprise ->
            val teams =
                teamRepository
                    .findAllByEnterpriseId(enterprise.id!!)
                    .map { team ->
                        val userIds = teamMemberRepository.findAllByTeamId(team.id!!).map { it.userId }.toList()
                        TeamWithMembers(id = team.id, name = team.name, description = team.description, userIds = userIds)
                    }.toList()
            EnterpriseWithTeams(
                id = enterprise.id,
                name = enterprise.name,
                phoneNumber = enterprise.phoneNumber,
                website = enterprise.website,
                email = enterprise.email,
                description = enterprise.description,
                teams = teams,
            )
        }
}
