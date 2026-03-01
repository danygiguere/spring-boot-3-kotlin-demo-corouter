package com.example.corouterdemo.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(description = "Team registration request")
data class TeamRequest(
    @field:NotNull(message = "{team.enterpriseId.required}")
    @field:Positive(message = "{team.enterpriseId.positive}")
    @Schema(example = "1")
    val enterpriseId: Long?,
    @field:NotBlank(message = "{team.name.required}")
    @field:Size(min = 2, max = 100, message = "{team.name.size}")
    @Schema(example = "Engineering")
    val name: String?,
    @field:Size(max = 500, message = "{team.description.size}")
    @Schema(example = "Responsible for all technical development")
    val description: String? = null,
)
