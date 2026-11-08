package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.User
import com.example.corouterdemo.dto.MeResponse
import com.example.corouterdemo.dto.ProfileRequest
import com.example.corouterdemo.dto.ProfileResponse
import com.example.corouterdemo.dto.UserResponse
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.repository.ProfileRepository
import com.example.corouterdemo.repository.UserRepository
import com.example.corouterdemo.security.AuthenticatedUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
) {
    suspend fun me(me: AuthenticatedUser): MeResponse = MeResponse(requireColleague(me, me.userId))

    fun findAll(
        me: AuthenticatedUser,
        afterId: Long,
        limit: Int,
    ): Flow<UserResponse> = userRepository.findPageInEnterprise(me.enterpriseId, afterId, limit).map(::UserResponse)

    suspend fun findById(
        me: AuthenticatedUser,
        id: Long,
    ): UserResponse = UserResponse(requireColleague(me, id))

    suspend fun findProfile(
        me: AuthenticatedUser,
        userId: Long,
    ): ProfileResponse {
        val user = requireColleague(me, userId)
        val profile =
            profileRepository.findByUserId(user.id!!)
                ?: throw AppException.NotFound("error.profile.not.found", arrayOf(userId))
        return ProfileResponse(profile)
    }

    /** Only the caller's own profile is writable; the target comes from the token, not the request. */
    suspend fun replaceMyProfile(
        me: AuthenticatedUser,
        request: ProfileRequest,
    ): ProfileResponse {
        val user = requireColleague(me, me.userId)
        return ProfileResponse(profileRepository.upsert(user.id!!, request.bio, request.avatarUrl))
    }

    /** A user of the caller's enterprise; any other user is reported as not found (no cross-tenant probing). */
    suspend fun requireColleague(
        me: AuthenticatedUser,
        userId: Long,
    ): User =
        userRepository.findInEnterprise(userId, me.enterpriseId)
            ?: throw AppException.NotFound("error.user.not.found", arrayOf(userId))
}
