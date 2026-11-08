package com.example.corouterdemo.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("CoRouter Demo API")
                    .description("Spring Boot 3 + Kotlin + WebFlux with coRouter and coroutines")
                    .version("1.0.0"),
            ).components(
                Components().addSecuritySchemes(
                    BEARER,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            ).addSecurityItem(SecurityRequirement().addList(BEARER))

    private companion object {
        const val BEARER = "bearerAuth"
    }
}
