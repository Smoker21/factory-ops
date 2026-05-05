package com.factoryops.interfaces.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "accountName is required")
    @field:Size(max = 30, message = "accountName must be at most 30 characters")
    val accountName: String = "",

    @field:NotBlank(message = "password is required")
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
