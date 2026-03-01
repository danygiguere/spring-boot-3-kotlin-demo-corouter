package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "User registration request")
data class UserRequest(
    @field:NotBlank(message = "{user.name.required}")
    @field:Size(min = 2, max = 100, message = "{user.name.size}")
    @Schema(example = "Jane Smith")
    val name: String?,
    @field:NotBlank(message = "{user.email.required}")
    @field:Email(message = "{user.email.invalid}")
    @Schema(example = "jane.smith@example.com")
    val email: String?,
    @field:Size(max = 20, message = "{user.phoneNumber.size}")
    @field:Pattern(
        regexp = "^\\+?[0-9\\s\\-().]{7,20}$",
        message = "{user.phoneNumber.pattern}",
    )
    @Schema(example = "+1-555-987-6543")
    val phoneNumber: String? = null,
)
