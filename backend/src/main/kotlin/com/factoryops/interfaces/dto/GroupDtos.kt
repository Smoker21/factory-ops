package com.factoryops.interfaces.dto

import com.factoryops.domain.group.Group
import com.factoryops.domain.group.GroupMembership
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.domain.group.GroupSettings
import com.factoryops.domain.group.QaSettings
import com.factoryops.domain.shared.enums.Role
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CreateGroupRequest(
    @field:NotBlank(message = "organizationId is required")
    val organizationId: String = "",

    @field:NotBlank(message = "type is required")
    val type: String = "",

    @field:NotBlank(message = "name is required")
    @field:Size(min = 1, max = 120)
    val name: String = "",

    @field:NotBlank(message = "code is required")
    @field:Size(min = 1, max = 64)
    val code: String = "",

    val attributes: Map<String, Any?> = emptyMap(),
    val leaderId: String? = null
)

data class UpdateGroupRequest(
    val name: String? = null,
    val type: String? = null,
    val attributes: Map<String, Any?>? = null
)

data class UpdateGroupSettingsRequest(
    val qa: QaSettingsDto? = null,
    val extras: Map<String, Any?>? = null
)

data class QaSettingsDto(
    val dualSignRequired: Boolean = false,
    val requiredReviewerRoles: List<String> = emptyList()
)

data class AddGroupMemberRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String = "",
    val role: String = "MEMBER"
)

data class TransferGroupLeaderRequest(
    @field:NotBlank(message = "newLeaderId is required")
    val newLeaderId: String = "",
    val reason: String? = null
)

data class GroupResponse(
    val id: String,
    val rootOrgId: String,
    val organizationId: String,
    val type: String,
    val name: String,
    val code: String,
    val leaderId: String?,
    val attributes: Map<String, Any?>,
    val settings: GroupSettingsResponse,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val schemaVersion: Int
)

data class GroupSettingsResponse(
    val qa: QaSettingsResponse,
    val extras: Map<String, Any?>
)

data class QaSettingsResponse(
    val dualSignRequired: Boolean,
    val requiredReviewerRoles: List<String>
)

data class GroupMembershipResponse(
    val id: String,
    val rootOrgId: String,
    val groupId: String,
    val userId: String,
    val role: String,
    val joinedAt: OffsetDateTime,
    val leftAt: OffsetDateTime?
)

data class GroupPageResponse(
    val items: List<GroupResponse>,
    val pageInfo: PageInfo
)

fun Group.toResponse(): GroupResponse = GroupResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    organizationId = organizationId,
    type = type,
    name = name,
    code = code,
    leaderId = leaderId,
    attributes = attributes,
    settings = settings.toResponse(),
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC),
    schemaVersion = schemaVersion
)

fun GroupSettings.toResponse(): GroupSettingsResponse = GroupSettingsResponse(
    qa = QaSettingsResponse(qa.dualSignRequired, qa.requiredReviewerRoles.map { it.name }),
    extras = extras
)

fun GroupMembership.toResponse(): GroupMembershipResponse = GroupMembershipResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    groupId = groupId,
    userId = userId,
    role = role.name,
    joinedAt = joinedAt.atOffset(ZoneOffset.UTC),
    leftAt = leftAt?.atOffset(ZoneOffset.UTC)
)

fun QaSettingsDto.toDomain(): QaSettings = QaSettings(
    dualSignRequired = dualSignRequired,
    requiredReviewerRoles = requiredReviewerRoles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
)
