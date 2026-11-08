package com.example.corouterdemo.router

import com.example.corouterdemo.support.IntegrationTest
import org.junit.jupiter.api.Test

class OpenApiTest : IntegrationTest() {
    @Test
    fun `swagger ui redirects`() {
        webTestClient
            .get()
            .uri("/swagger-ui.html")
            .exchange()
            .expectStatus()
            .is3xxRedirection
    }

    @Test
    fun `api docs list every route`() {
        webTestClient
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.paths['/auth/dev-token'].post")
            .exists()
            .jsonPath("$.paths['/enterprises/current'].get")
            .exists()
            .jsonPath("$.paths['/users'].get")
            .exists()
            .jsonPath("$.paths['/users/me'].get")
            .exists()
            .jsonPath("$.paths['/users/me/profile'].put")
            .exists()
            .jsonPath("$.paths['/users/{id}'].get")
            .exists()
            .jsonPath("$.paths['/users/{id}/profile'].get")
            .exists()
            .jsonPath("$.paths['/users/{id}/posts'].get")
            .exists()
            .jsonPath("$.paths['/users/{id}/liked-posts'].get")
            .exists()
            .jsonPath("$.paths['/posts'].get")
            .exists()
            .jsonPath("$.paths['/posts'].post")
            .exists()
            .jsonPath("$.paths['/posts/{id}'].put")
            .exists()
            .jsonPath("$.paths['/posts/{id}'].delete")
            .exists()
            .jsonPath("$.paths['/posts/{id}/images'].get")
            .exists()
            .jsonPath("$.paths['/posts/{id}/likes'].put")
            .exists()
            .jsonPath("$.components.securitySchemes.bearerAuth")
            .exists()
    }
}
