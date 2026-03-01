package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

@Schema(description = "Enterprise registration request")
data class EnterpriseRequest(
    @field:NotBlank(message = "{enterprise.name.required}")
    @field:Size(min = 2, max = 100, message = "{enterprise.name.size}")
    @Schema(example = "Acme Corp")
    val name: String?,
    @field:Size(max = 20, message = "{enterprise.phoneNumber.size}")
    @field:Pattern(
        regexp = "^\\+?[0-9\\s\\-().]{7,20}$",
        message = "{enterprise.phoneNumber.pattern}",
    )
    @Schema(example = "+1-555-000-0000")
    val phoneNumber: String? = null,
    @field:URL(message = "{enterprise.website.url}")
    @field:Size(max = 255, message = "{enterprise.website.size}")
    @Schema(example = "https://acme.com")
    val website: String? = null,
    @field:NotBlank(message = "{enterprise.email.required}")
    @field:Email(message = "{enterprise.email.invalid}")
    @Schema(example = "contact@acme.com")
    val email: String?,
    @field:Size(max = 500, message = "{enterprise.description.size}")
    @Schema(example = "A leading provider of innovative solutions")
    val description: String? = null,
)
