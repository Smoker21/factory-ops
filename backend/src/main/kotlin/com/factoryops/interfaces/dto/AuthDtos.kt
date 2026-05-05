package com.factoryops.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "orgCode is required")
    @field:Size(max = 64, message = "orgCode must be at most 64 characters")
    val orgCode: String = "",

    @field:NotBlank(message = "accountName is required")
    @field:Size(max = 30, message = "accountName must be at most 30 characters")
    val accountName: String = "",

    @field:NotBlank(message = "password is required")
    @field:Size(max = 128, message = "password must be at most 128 characters")
    val password: String = ""
)

data class TokenPairResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

data class RefreshRequest(
    @field:NotBlank(message = "refreshToken is required")
    val refreshToken: String = ""
)

data class LogoutRequest(
    @field:NotBlank(message = "refreshToken is required")
    val refreshToken: String = ""
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "currentPassword is required")
    val currentPassword: String = "",

    @field:NotBlank(message = "newPassword is required")
    @field:Size(min = 12, message = "newPassword must be at least 12 characters")
    val newPassword: String = ""
)
