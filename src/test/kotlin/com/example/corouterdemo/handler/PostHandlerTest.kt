package com.example.corouterdemo.handler

import com.example.corouterdemo.dto.PostDetails
import com.example.corouterdemo.dto.PostSummary
import com.example.corouterdemo.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import java.util.UUID

class PostHandlerTest : IntegrationTest() {
    @Test
    fun `create - stores post and images as the authenticated user`() {
        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", newKey())
            .bodyValue(NEW_POST)
            .exchange()
            .expectStatus()
            .isCreated
            .expectHeader()
            .valueMatches("Location", "/posts/\\d+")
            .expectHeader()
            .valueEquals("Idempotent-Replayed", "false")
            .expectBody()
            .jsonPath("$.author.id")
            .isEqualTo(3)
            .jsonPath("$.images[*].position")
            .isEqualTo(listOf(1, 2))
            .jsonPath("$.likeCount")
            .isEqualTo(0)
    }

    @Test
    fun `create - retried with same key returns the same post`() {
        val author = asUser(AUTHOR)
        val key = newKey()
        val first = create(author, key)

        val retry =
            author
                .post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isCreated
                .expectHeader()
                .valueEquals("Idempotent-Replayed", "true")
                .expectBody<PostDetails>()
                .returnResult()
                .responseBody

        assertThat(retry).isEqualTo(first)
    }

    @Test
    fun `create - same key with different body returns 409`() {
        val author = asUser(AUTHOR)
        val key = newKey()
        create(author, key)

        author
            .post()
            .uri("/posts")
            .header("Idempotency-Key", key)
            .bodyValue(mapOf("content" to "Something else"))
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/idempotency-key-reused")
    }

    @Test
    fun `create - same key from another user is independent`() {
        val key = newKey()
        val byAuthor = create(asUser(AUTHOR), key)
        val byColleague = create(asUser(COLLEAGUE), key)

        assertThat(byColleague.id).isNotEqualTo(byAuthor.id)
    }

    @Test
    fun `create - missing or malformed idempotency key returns 400`() {
        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .bodyValue(NEW_POST)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/header-required")

        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", "short")
            .bodyValue(NEW_POST)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/idempotency-key-invalid")
    }

    @Test
    fun `create - invalid body returns 422 with field errors`() {
        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", newKey())
            .bodyValue(mapOf("content" to " ", "imageUrls" to List(11) { "https://images.example.com/x.jpg" }))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors.content[0]")
            .isEqualTo("Content is required")
            .jsonPath("$.errors.imageUrls[0]")
            .isEqualTo("A post can have at most 10 images")

        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", newKey())
            .bodyValue(mapOf("content" to "ok", "imageUrls" to listOf("http://insecure.example.com/x.jpg")))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.errors['imageUrls[0]'][0]")
            .isEqualTo("Image URL must be a valid https URL")
    }

    @Test
    fun `create - author field in body is rejected`() {
        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", newKey())
            .bodyValue(mapOf("content" to "Hello", "userId" to 1))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `create - body above limit returns 413`() {
        asUser(AUTHOR)
            .post()
            .uri("/posts")
            .header("Idempotency-Key", newKey())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("content" to "x".repeat(70_000)))
            .exchange()
            .expectStatus()
            .isEqualTo(413)
    }

    @Test
    fun `find by id - post of another enterprise is 404`() {
        val post = create(asUser(AUTHOR), newKey())

        asUser(OUTSIDER)
            .get()
            .uri("/posts/{id}", post.id)
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/post-not-found")
    }

    @Test
    fun `update - only by author`() {
        val post = create(asUser(AUTHOR), newKey())

        asUser(COLLEAGUE)
            .put()
            .uri("/posts/{id}", post.id)
            .bodyValue(mapOf("content" to "Hijacked"))
            .exchange()
            .expectStatus()
            .isForbidden
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/post-not-author")

        asUser(AUTHOR)
            .put()
            .uri("/posts/{id}", post.id)
            .bodyValue(mapOf("content" to "Edited"))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.content")
            .isEqualTo("Edited")
            .jsonPath("$.images.length()")
            .isEqualTo(2)
    }

    @Test
    fun `delete - only by author, and the key cannot recreate it`() {
        val author = asUser(AUTHOR)
        val key = newKey()
        val post = create(author, key)

        asUser(COLLEAGUE)
            .delete()
            .uri("/posts/{id}", post.id)
            .exchange()
            .expectStatus()
            .isForbidden
        author
            .delete()
            .uri("/posts/{id}", post.id)
            .exchange()
            .expectStatus()
            .isNoContent
        author
            .get()
            .uri("/posts/{id}", post.id)
            .exchange()
            .expectStatus()
            .isNotFound

        author
            .post()
            .uri("/posts")
            .header("Idempotency-Key", key)
            .bodyValue(NEW_POST)
            .exchange()
            .expectStatus()
            .isEqualTo(409)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/idempotency-key-gone")
    }

    @Test
    fun `like and unlike - are idempotent`() {
        val post = create(asUser(AUTHOR), newKey())
        val colleague = asUser(COLLEAGUE)

        repeat(2) {
            colleague
                .put()
                .uri("/posts/{id}/likes", post.id)
                .exchange()
                .expectStatus()
                .isNoContent
        }
        colleague
            .get()
            .uri("/posts/{id}/likes", post.id)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(4))

        repeat(2) {
            colleague
                .delete()
                .uri("/posts/{id}/likes", post.id)
                .exchange()
                .expectStatus()
                .isNoContent
        }
        colleague
            .get()
            .uri("/posts/{id}", post.id)
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.likeCount")
            .isEqualTo(0)
    }

    @Test
    fun `like - post of another enterprise is 404`() {
        val post = create(asUser(AUTHOR), newKey())

        asUser(OUTSIDER)
            .put()
            .uri("/posts/{id}/likes", post.id)
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `feed - is keyset paginated with batched counts`() {
        // Post p has (p % 4) images and (p % 7) likes in the seed.
        asUser(ALICE)
            .get()
            .uri("/posts?beforeId=8&limit=3")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(7, 6, 5))
            .jsonPath("$[0].imageCount")
            .isEqualTo(3)
            .jsonPath("$[0].likeCount")
            .isEqualTo(0)
            .jsonPath("$[1].likeCount")
            .isEqualTo(6)
            .jsonPath("$[2].author.name")
            .isEqualTo("Bob")
    }

    @Test
    fun `feed - is scoped to caller's enterprise`() {
        val page =
            asUser(EVE)
                .get()
                .uri("/posts?limit=100")
                .exchange()
                .expectStatus()
                .isOk
                .expectBodyList<PostSummary>()
                .returnResult()
                .responseBody!!

        assertThat(page).isNotEmpty
        assertThat(page).allSatisfy { assertThat(it.author.id).isBetween(11L, 20L) }
    }

    @Test
    fun `error correlationId matches the response header`() {
        val result =
            asUser(ALICE)
                .get()
                .uri("/posts/999999")
                .exchange()
                .expectStatus()
                .isNotFound
                .expectBody<Map<String, Any>>()
                .returnResult()

        assertThat(result.responseBody!!["correlationId"]).isEqualTo(result.responseHeaders.getFirst("X-Correlation-Id"))
    }

    private fun create(
        client: WebTestClient,
        key: String,
    ): PostDetails =
        client
            .post()
            .uri("/posts")
            .header("Idempotency-Key", key)
            .bodyValue(NEW_POST)
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody<PostDetails>()
            .returnResult()
            .responseBody!!

    private fun newKey() = UUID.randomUUID().toString()

    private companion object {
        val NEW_POST =
            mapOf(
                "content" to "Hello",
                "imageUrls" to listOf("https://images.example.com/a.jpg", "https://images.example.com/b.jpg"),
            )
    }
}
