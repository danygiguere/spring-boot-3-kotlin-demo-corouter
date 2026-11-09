package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.CreatePostRequest
import com.example.corouterdemo.dto.UpdatePostRequest
import com.example.corouterdemo.exception.AppException
import com.example.corouterdemo.extension.afterId
import com.example.corouterdemo.extension.awaitValidBody
import com.example.corouterdemo.extension.beforeId
import com.example.corouterdemo.extension.limit
import com.example.corouterdemo.extension.pathId
import com.example.corouterdemo.security.CurrentUser
import com.example.corouterdemo.service.PostService
import jakarta.validation.Validator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import java.net.URI

@Component
class PostHandler(
    private val postService: PostService,
    private val currentUser: CurrentUser,
    private val validator: Validator,
) {
    suspend fun feed(request: ServerRequest): ServerResponse {
        val beforeId = request.beforeId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(postService.feed(beforeId, limit))
    }

    suspend fun create(request: ServerRequest): ServerResponse {
        val idempotencyKey = request.idempotencyKey()
        val me = currentUser.get()
        val body = request.awaitValidBody<CreatePostRequest>(validator)
        val created = postService.create(me, idempotencyKey, body)
        return ServerResponse
            .created(URI.create("/posts/${created.post.id}"))
            .header(REPLAYED_HEADER, created.replayed.toString())
            .bodyValueAndAwait(created.post)
    }

    suspend fun findById(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        return ServerResponse.ok().bodyValueAndAwait(postService.findById(id))
    }

    suspend fun update(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        val me = currentUser.get()
        val body = request.awaitValidBody<UpdatePostRequest>(validator)
        return ServerResponse.ok().bodyValueAndAwait(postService.update(me, id, body))
    }

    suspend fun delete(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        postService.delete(currentUser.get(), id)
        return ServerResponse.noContent().buildAndAwait()
    }

    suspend fun findImages(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        return ServerResponse.ok().bodyAndAwait(postService.findImages(id))
    }

    suspend fun findLikers(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        val afterId = request.afterId()
        val limit = request.limit()
        return ServerResponse.ok().bodyAndAwait(postService.findLikers(id, afterId, limit))
    }

    suspend fun like(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        postService.like(currentUser.get(), id)
        return ServerResponse.noContent().buildAndAwait()
    }

    suspend fun unlike(request: ServerRequest): ServerResponse {
        val id = request.pathId("id")
        postService.unlike(currentUser.get(), id)
        return ServerResponse.noContent().buildAndAwait()
    }

    /** Required: without it, a retried or double-submitted create would publish the post twice. */
    private fun ServerRequest.idempotencyKey(): String {
        val key = headers().firstHeader(IDEMPOTENCY_KEY_HEADER)
        if (key.isNullOrBlank()) throw AppException.BadRequest("error.header.required", arrayOf(IDEMPOTENCY_KEY_HEADER))
        if (!IDEMPOTENCY_KEY.matches(key)) throw AppException.BadRequest("error.idempotency.key.invalid")
        return key
    }

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val REPLAYED_HEADER = "Idempotent-Replayed"
        private val IDEMPOTENCY_KEY = Regex("[A-Za-z0-9_-]{8,128}")
    }
}
