package com.example.corouterdemo.router

import com.example.corouterdemo.shared.config.SharedTestContainers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserRouterSwaggerTest : SharedTestContainers() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `swagger ui returns 3xx redirect`() {
        webTestClient
            .get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus()
            .is3xxRedirection
    }

    @Test
    fun `swagger api-docs returns 200 and contains all user routes`() {
        webTestClient
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.paths['/users']")
            .exists()
            .jsonPath("$.paths['/users/{id}']")
            .exists()
            .jsonPath("$.paths['/users/{userId}/teams']")
            .exists()
            .jsonPath("$.paths['/users/{userId}/teams/{teamId}']")
            .exists()
    }
}
