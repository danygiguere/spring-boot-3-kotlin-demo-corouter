package com.example.corouterdemo.handler

import com.example.corouterdemo.support.IntegrationTest
import org.junit.jupiter.api.Test

class UserHandlerTest : IntegrationTest() {
    @Test
    fun `me - returns the token user with email`() {
        asUser(ALICE)
            .get()
            .uri("/users/me")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.id")
            .isEqualTo(1)
            .jsonPath("$.email")
            .isEqualTo(ALICE)
    }

    @Test
    fun `list - is keyset paginated`() {
        asUser(ALICE)
            .get()
            .uri("/users?afterId=1&limit=3")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(3)
            .jsonPath("$[0].id")
            .isEqualTo(2)
            .jsonPath("$[2].id")
            .isEqualTo(4)
            // Other users are listed without their email.
            .jsonPath("$[0].email")
            .doesNotExist()
    }

    @Test
    fun `list - rejects limit above maximum`() {
        asUser(ALICE)
            .get()
            .uri("/users?limit=500")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/query-param-invalid")
            .jsonPath("$.detail")
            .isEqualTo("Query parameter 'limit' must be a number between 1 and 100")
    }

    @Test
    fun `find by id - unknown user is 404 with localized detail`() {
        asUser(ALICE)
            .get()
            .uri("/users/999999")
            .header("Accept-Language", "fr")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/user-not-found")
            .jsonPath("$.detail")
            .isEqualTo("Utilisateur 999999 introuvable")
            .jsonPath("$.instance")
            .isEqualTo("/users/999999")
    }

    @Test
    fun `find by id - returns 400 when id is not a number`() {
        asUser(ALICE)
            .get()
            .uri("/users/abc")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/path-param-invalid")
    }

    @Test
    fun `profile - is readable by other users`() {
        asUser(BOB)
            .get()
            .uri("/users/1/profile")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.userId")
            .isEqualTo(1)
            .jsonPath("$.bio")
            .isEqualTo("Bio of user 1")
    }

    @Test
    fun `replace my profile - upserts the caller's profile only`() {
        val user = asUser(OTHER_USER)
        repeat(2) {
            user
                .put()
                .uri("/users/me/profile")
                .bodyValue(mapOf("bio" to "Updated bio", "avatarUrl" to "https://images.example.com/u4.png"))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(4)
                .jsonPath("$.bio")
                .isEqualTo("Updated bio")
        }
    }

    @Test
    fun `replace my profile - rejects non-https avatar with localized message`() {
        asUser(OTHER_USER)
            .put()
            .uri("/users/me/profile")
            .header("Accept-Language", "fr")
            .bodyValue(mapOf("avatarUrl" to "javascript:alert(1)"))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/validation-failed")
            .jsonPath("$.errors.avatarUrl[0]")
            .isEqualTo("L'URL de l'avatar doit être une URL https valide")
    }

    @Test
    fun `replace my profile - rejects unknown fields`() {
        asUser(OTHER_USER)
            .put()
            .uri("/users/me/profile")
            .bodyValue(mapOf("bio" to "x", "userId" to 1))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `posts - are the user's posts newest first`() {
        asUser(BOB)
            .get()
            .uri("/users/1/posts")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(4, 3, 2, 1))
            .jsonPath("$[0].author.name")
            .isEqualTo("Alice")
    }

    @Test
    fun `posts - of an unknown user is 404`() {
        asUser(ALICE)
            .get()
            .uri("/users/999999/posts")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/user-not-found")
    }

    @Test
    fun `images - are the images of the user's posts, newest first`() {
        // Alice's posts 1 to 3 have 1, 2 and 3 images: ids 1 to 6.
        asUser(BOB)
            .get()
            .uri("/users/1/images?beforeId=6&limit=3")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[*].id")
            .isEqualTo(listOf(5, 4, 3))
            .jsonPath("$[*].postId")
            .isEqualTo(listOf(3, 3, 2))
    }

    @Test
    fun `images - of an unknown user is 404`() {
        asUser(ALICE)
            .get()
            .uri("/users/999999/images")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/user-not-found")
    }
}
