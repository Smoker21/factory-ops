package com.factoryops.interfaces.dto

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ========= Request DTOs =========

data class CreateOrganizationRequest(
    val parentId: String? = null,

    @field:NotBlank(message = "type is required")
    val type: String = "",

    @field:NotBlank(message = "name is required")
    @field:Size(min = 1, max = 120, message = "name must be 1-120 characters")
    val name: String = "",

    @field:NotBlank(message = "code is required")
    @field:Size(min = 1, max = 64, message = "code must be 1-64 characters")
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "code must only contain lowercase letters, digits, and hyphens")
    val code: String = "",

    val managerId: String? = null,
    val leaderIds: List<String> = emptyList(),
    val timezone: String? = null,
    val locale: String? = null,
    val settings: OrgSettingsDto? = null,
    val initialOrgAdmin: InitialOrgAdminDto? = null
)

data class InitialOrgAdminDto(
    @field:NotBlank
    @field:Size(max = 30)
    val accountName: String = "",
    val defaultPassword: String = "ChangeMe@123456"
)

data class UpdateOrganizationRequest(
    val name: String? = null,
    val parentId: String? = null,
    val type: String? = null,
    val timezone: String? = null,
    val locale: String? = null,
    val settings: OrgSettingsDto? = null
)

data class OrgSettingsDto(
    val orgMaxDepth: Int = 5,
    val leafTypes: List<String> = listOf("SECTION"),
    val attachmentMaxBytes: Long = 52428800L,
    val extras: Map<String, Any?> = emptyMap()
)

data class TransferManagerRequest(
    val newManagerId: String? = null,
    val reason: String? = null
)

data class AddLeaderRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String = "",
    val reason: String? = null
)

// ========= Response DTOs =========

data class OrganizationResponse(
    val id: String,
    val rootOrgId: String,
    val parentId: String?,
    val type: String,
    val name: String,
    val code: String,
    val managerId: String?,
    val leaderIds: List<String>,
    val ancestorIds: List<String>,
    val isLeaf: Boolean,
    val depth: Int,
    val timezone: String?,
    val locale: String?,
    val settings: OrgSettingsDto?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val schemaVersion: Int
)

data class OrganizationPageResponse(
    val items: List<OrganizationResponse>,
    val pageInfo: PageInfo
)

data class PageInfo(
    val nextCursor: String?,
    val hasMore: Boolean
)

data class HistoryEntryResponse(
    val actorId: String,
    val action: String,
    val at: OffsetDateTime,
    val payload: Map<String, Any?>
)

// ========= Converters =========

fun Organization.toResponse(): OrganizationResponse = OrganizationResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    parentId = parentId,
    type = type,
    name = name,
    code = code,
    managerId = managerId,
    leaderIds = leaderIds,
    ancestorIds = ancestorIds,
    isLeaf = isLeaf,
    depth = depth,
    timezone = timezone,
    locale = locale,
    settings = settings?.let { OrgSettingsDto(it.orgMaxDepth, it.leafTypes, it.attachmentMaxBytes, it.extras) },
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC),
    schemaVersion = schemaVersion
)

fun OrgSettingsDto.toDomain(): OrgSettings = OrgSettings(
    orgMaxDepth = orgMaxDepth,
    leafTypes = leafTypes,
    attachmentMaxBytes = attachmentMaxBytes,
    extras = extras
)

fun AuditEntry.toResponse(): HistoryEntryResponse = HistoryEntryResponse(
    actorId = actorId,
    action = action,
    at = at.atOffset(ZoneOffset.UTC),
    payload = payload
)

fun User.toOrganizationLeaderResponse(): UserResponse = UserResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    accountName = accountName,
    employeeNo = employeeNo,
    email = email,
    displayName = displayName,
    roles = roles.map { it.name },
    active = active,
    createdAt = createdAt.atOffset(ZoneOffset.UTC)
)
