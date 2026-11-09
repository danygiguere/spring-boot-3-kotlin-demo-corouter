package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.ProfileRequest
import com.example.corouterdemo.extension.afterId
import com.example.corouterdemo.extension.awaitValidBody
import com.example.corouterdemo.extension.beforeId
import com.example.corouterdemo.extension.limit
import com.example.corouterdemo.extension.pathId
import com.example.corouterdemo.security.CurrentUser
import com.example.corouterdemo.service.PostService
import com.example.corouterdemo.service.UserService
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class UserHandler(
    private val userService: UserService,
    private val postService: PostService,
    private val currentUser: CurrentUser,
    private val validator: Validator,
) {
    suspend fun me(request: ServerRequest): ServerResponse = ServerResponse.ok().bodyValueAndAwait(userService.me(currentUser.get()))

    suspend fun findAll(request: ServerRequest): ServerResponse {
        val afterId = request.afterId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(userService.findAll(afterId, limit))
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        return ServerResponse.ok().bodyValueAndAwait(userService.findById(id))
    }

    suspend fun findProfile(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        return ServerResponse.ok().bodyValueAndAwait(userService.findProfile(id))
    }

    suspend fun replaceMyProfile(request: ServerRequest): ServerResponse {
        val me = currentUser.get()
        val body = request.awaitValidBody<ProfileRequest>(validator)
        return ServerResponse.ok().bodyValueAndAwait(userService.replaceMyProfile(me, body))
    }

    suspend fun findPosts(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        val beforeId = request.beforeId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(postService.findByAuthor(id, beforeId, limit))
    }

    suspend fun findLikedPosts(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        val beforeId = request.beforeId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(postService.findLikedBy(id, beforeId, limit))
    }

    suspend fun findImages(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        val beforeId = request.beforeId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(userService.findImages(id, beforeId, limit))
    }
}
