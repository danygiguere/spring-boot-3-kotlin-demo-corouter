package com.example.corouterdemo.router

import com.example.corouterdemo.shared.config.SharedTestContainers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnterpriseRouterSwaggerTest : SharedTestContainers() {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `swagger api-docs contains all enterprise routes`() {
        webTestClient
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.paths['/enterprises']")
            .exists()
            .jsonPath("$.paths['/enterprises/search']")
            .exists()
            .jsonPath("$.paths['/enterprises/{id}']")
            .exists()
    }
}
