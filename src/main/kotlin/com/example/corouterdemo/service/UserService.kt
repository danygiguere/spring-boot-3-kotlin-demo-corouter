package com.example.corouterdemo.service

import com.example.corouterdemo.domain.entity.User
import com.example.corouterdemo.dto.CursorPage
import com.example.corouterdemo.dto.ImageResponse
import com.example.corouterdemo.dto.MeResponse
import com.example.corouterdemo.dto.ProfileRequest
import com.example.corouterdemo.dto.ProfileResponse
import com.example.corouterdemo.dto.UserResponse
import com.example.corouterdemo.error.NotFoundException
import com.example.corouterdemo.repository.ImageRepository
import com.example.corouterdemo.repository.ProfileRepository
import com.example.corouterdemo.repository.UserRepository
import com.example.corouterdemo.security.AuthenticatedUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val imageRepository: ImageRepository,
) {
    suspend fun me(me: AuthenticatedUser): MeResponse = MeResponse(requireUser(me.userId))

    suspend fun findAll(
        cursor: Long,
        limit: Int,
    ): CursorPage<UserResponse> = CursorPage.of(userRepository.findPage(cursor, limit + 1).map(::UserResponse).toList(), limit) { it.id }

    suspend fun findById(id: Long): UserResponse = UserResponse(requireUser(id))

    suspend fun findProfile(userId: Long): ProfileResponse {
        val user = requireUser(userId)
        val profile =
            profileRepository.findByUserId(user.id!!)
                ?: throw NotFoundException("error.profile.notFound", listOf(userId))
        return ProfileResponse(profile)
    }

    /** Only the caller's own profile is writable; the target comes from the token, not the request. */
    suspend fun replaceMyProfile(
        me: AuthenticatedUser,
        request: ProfileRequest,
    ): ProfileResponse {
        val user = requireUser(me.userId)
        return ProfileResponse(profileRepository.upsert(user.id!!, request.bio, request.avatarUrl))
    }

    /** The existence check runs before the [Flow] is returned, so a 404 is raised before any byte is written. */
    suspend fun findImages(
        userId: Long,
        cursor: Long,
        limit: Int,
    ): CursorPage<ImageResponse> {
        requireUser(userId)
        return CursorPage.of(imageRepository.findByUser(userId, cursor, limit + 1).map(::ImageResponse).toList(), limit) { it.id }
    }

    suspend fun requireUser(userId: Long): User =
        userRepository.findById(userId) ?: throw NotFoundException("error.user.notFound", listOf(userId))
}
