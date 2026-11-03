package com.example.corouterdemo.controller

import com.example.corouterdemo.dto.UserRequest
import com.example.corouterdemo.shared.config.SharedTestContainers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest : SharedTestContainers() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `create user - success returns 201 with user data`() {
        webTestClient
            .post()
            .uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(UserRequest(name = "Jane Smith", email = "jane.smith@example.com", phoneNumber = "+1-555-987-6543"))
            .exchange()
            .expectStatus()
            .isCreated
            .expectBody()
            .jsonPath("$.id")
            .isNumber
            .jsonPath("$.name")
            .isEqualTo("Jane Smith")
            .jsonPath("$.email")
            .isEqualTo("jane.smith@example.com")
            .jsonPath("$.phoneNumber")
            .isEqualTo("+1-555-987-6543")
    }

    @Test
    fun `create user - returns 422 when required fields are blank`() {
        webTestClient
            .post()
            .uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("name" to "", "email" to ""))
            .exchange()
            .expectStatus()
            .isEqualTo(422)
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/validation-failed")
            .jsonPath("$.status")
            .isEqualTo(422)
            .jsonPath("$.correlationId")
            .isNotEmpty
            .jsonPath("$.errors.name")
            .isArray
            .jsonPath("$.errors.email")
            .isArray
    }

    @Test
    fun `get user - returns 404 with localized detail when not found`() {
        webTestClient
            .get()
            .uri("/users/999999")
            .header("Accept-Language", "fr")
            .exchange()
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/user-not-found")
            .jsonPath("$.status")
            .isEqualTo(404)
            .jsonPath("$.detail")
            .isEqualTo("Utilisateur 999999 introuvable")
            .jsonPath("$.instance")
            .isEqualTo("/users/999999")
            .jsonPath("$.correlationId")
            .isNotEmpty
    }

    @Test
    fun `error correlationId matches the response header`() {
        val result =
            webTestClient
                .get()
                .uri("/users/999999")
                .exchange()
                .expectStatus()
                .isNotFound
                .expectBody(Map::class.java)
                .returnResult()
        val header = result.responseHeaders.getFirst("X-Correlation-Id")
        org.junit.jupiter.api.Assertions
            .assertNotNull(header)
        org.junit.jupiter.api.Assertions
            .assertEquals(header, result.responseBody!!["correlationId"])
    }

    @Test
    fun `get user - returns 400 when id is not a number`() {
        webTestClient
            .get()
            .uri("/users/abc")
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.type")
            .isEqualTo("/errors/path-param-invalid")
            .jsonPath("$.detail")
            .isEqualTo("Path parameter 'id' must be a number")
    }
}
