package com.example.corouterdemo.service

import com.example.corouterdemo.dto.EnterpriseResponse
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.EnterpriseRepository
import com.example.corouterdemo.security.AuthenticatedUser
import org.springframework.stereotype.Service

@Service
class EnterpriseService(
    private val enterpriseRepository: EnterpriseRepository,
) {
    /** The caller's enterprise: the only one they can see. */
    suspend fun current(me: AuthenticatedUser): EnterpriseResponse =
        enterpriseRepository.findById(me.enterpriseId)?.let(::EnterpriseResponse)
            ?: throw AppException.NotFound("error.enterprise.not.found")
}
