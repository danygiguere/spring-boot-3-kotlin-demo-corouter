package com.example.corouterdemo.support

import com.example.corouterdemo.dto.TokenResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Base class for integration tests: one PostgreSQL Testcontainer for the whole suite, and the local settings of
 * `.env.example`, so the deterministic seed (`db/seed/R__dev_seed.sql`), `/auth/dev-token` and Swagger are available.
 *
 * Tests that write create their own posts (as users 3, 4 and 12) and never change seeded posts, likes or the users
 * 1, 2 and 11, so seed-based assertions hold in any order.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "server.address=127.0.0.1",
        "app.dev-token.enabled=true",
        "app.jwt.secret=test-only-key-at-least-32-bytes-long-0123456789",
    ],
)
abstract class IntegrationTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    /** A client authenticated as the seeded user with this email. */
    fun asUser(email: String): WebTestClient {
        val token =
            webTestClient
                .post()
                .uri("/auth/dev-token")
                .bodyValue(mapOf("email" to email))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<TokenResponse>()
                .returnResult()
                .responseBody!!
                .accessToken
        return webTestClient.mutate().defaultHeader("Authorization", "Bearer $token").build()
    }

    companion object {
        const val ALICE = "alice@example.com" // user 1, enterprise 1
        const val BOB = "bob@example.com" // user 2, enterprise 1
        const val EVE = "eve@example.com" // user 11, enterprise 2
        const val AUTHOR = "user3@example.com" // user 3, enterprise 1
        const val COLLEAGUE = "user4@example.com" // user 4, enterprise 1
        const val OUTSIDER = "user12@example.com" // user 12, enterprise 2

        private val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).withReuse(true).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username") { postgres.username }
            registry.add("spring.r2dbc.password") { postgres.password }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
        }
    }
}
