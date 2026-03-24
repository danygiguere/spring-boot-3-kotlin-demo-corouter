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
        enterpriseRepository.findById(id) ?: throw AppException.NotFound("error.enterprise.not.found", arrayOf(id))

    fun findAll(): Flow<Enterprise> = enterpriseRepository.findAll()

    suspend fun searchByName(name: String): List<EnterpriseWithTeams> {
        val enterprises = enterpriseRepository.findByNameContainingIgnoreCase(name).toList()
        if (enterprises.isEmpty()) return emptyList()

        val enterpriseIds = enterprises.map { it.id!! }
        val teamsByEnterpriseId = teamRepository.findAllByEnterpriseIdIn(enterpriseIds).toList().groupBy { it.enterpriseId }

        val teamIds = teamsByEnterpriseId.values.flatten().mapNotNull { it.id }
        val membersByTeamId =
            if (teamIds.isNotEmpty()) {
                teamMemberRepository.findAllByTeamIdIn(teamIds).toList().groupBy { it.teamId }
            } else {
                emptyMap()
            }

        return enterprises.map { enterprise ->
            val teams =
                teamsByEnterpriseId[enterprise.id].orEmpty().map { team ->
                    val userIds = membersByTeamId[team.id].orEmpty().map { it.userId }
                    TeamWithMembers(id = team.id, name = team.name, description = team.description, userIds = userIds)
                }
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
}
