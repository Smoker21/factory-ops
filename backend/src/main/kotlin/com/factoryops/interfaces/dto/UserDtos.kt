package com.factoryops.interfaces.dto

import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CreateUserRequest(
    @field:NotBlank(message = "accountName is required")
    @field:Size(max = 30, message = "accountName must be at most 30 characters")
    val accountName: String = "",

    val roles: List<String> = emptyList()
)

data class UpdateUserRequest(
    val roles: List<String>? = null,
    val active: Boolean? = null
)

data class UserResponse(
    val id: String,
    val rootOrgId: String,
    val accountName: String,
    val employeeNo: String,
    val email: String?,
    val displayName: String,
    val roles: List<String>,
    val active: Boolean,
    val createdAt: OffsetDateTime,
    val deletedAt: OffsetDateTime? = null
)

data class UserPageResponse(
    val items: List<UserResponse>,
    val pageInfo: PageInfo
)

/**
 * Response for user creation that includes the one-time temporary password.
 * The temporary password is shown only in this response and is never stored in plain text.
 */
data class CreateUserResponse(
    val user: UserResponse,
    /** One-time temporary password. Display to the authorized caller and do not log. */
    val temporaryPassword: String
)

fun User.toResponse(): UserResponse = UserResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    accountName = accountName,
    employeeNo = employeeNo,
    email = email,
    displayName = displayName,
    roles = roles.map { it.name },
    active = active,
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC)
)
