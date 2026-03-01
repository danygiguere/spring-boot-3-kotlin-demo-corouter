package com.example.corouterdemo.controller

import com.example.corouterdemo.dto.UserRequest
import com.example.corouterdemo.shared.config.SharedTestContainers
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
            .jsonPath("$.errors.name")
            .isArray
            .jsonPath("$.errors.email")
            .isArray
    }
}
