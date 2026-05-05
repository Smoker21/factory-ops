package com.factoryops.interfaces.dto

import com.factoryops.domain.membership.Membership
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.Project
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.TimeRange
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CreateProjectRequest(
    @field:NotBlank(message = "organizationId is required")
    val organizationId: String = "",

    @field:NotBlank(message = "name is required")
    @field:Size(min = 1, max = 120)
    val name: String = "",

    val descriptionMarkdown: String? = null,

    @field:NotBlank(message = "ownerId is required")
    val ownerId: String = "",

    val memberIds: List<String> = emptyList(),

    @field:NotEmpty(message = "groupIds must not be empty")
    val groupIds: List<String> = emptyList(),

    val schedule: TimeRangeDto? = null,
    val tags: List<String> = emptyList()
)

data class UpdateProjectRequest(
    val name: String? = null,
    val descriptionMarkdown: String? = null,
    val schedule: TimeRangeDto? = null,
    val tags: List<String>? = null
)

data class ChangeProjectStatusRequest(
    @field:NotBlank(message = "status is required")
    val status: String = "",
    val reason: String? = null
)

data class ChangeProjectOwnerRequest(
    @field:NotBlank(message = "newOwnerId is required")
    val newOwnerId: String = "",
    val reason: String? = null
)

data class AddProjectMemberRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String = "",
    val role: String = "MEMBER"
)

data class TimeRangeDto(
    val start: OffsetDateTime? = null,
    val due: OffsetDateTime? = null
) {
    fun toDomain(): TimeRange = TimeRange(
        start = start?.toInstant(),
        due = due?.toInstant()
    )
}

data class ProjectResponse(
    val id: String,
    val rootOrgId: String,
    val organizationId: String,
    val name: String,
    val descriptionMarkdown: String?,
    val status: String,
    val ownerId: String,
    val memberIds: List<String>,
    val groupIds: List<String>,
    val schedule: TimeRangeDto?,
    val tags: List<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val schemaVersion: Int
)

data class ProjectPageResponse(
    val items: List<ProjectResponse>,
    val pageInfo: PageInfo
)

data class MembershipResponse(
    val id: String,
    val rootOrgId: String,
    val projectId: String,
    val userId: String,
    val role: String,
    val joinedAt: OffsetDateTime
)

fun Project.toResponse(): ProjectResponse = ProjectResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    organizationId = organizationId,
    name = name,
    descriptionMarkdown = descriptionMarkdown,
    status = status.name,
    ownerId = ownerId,
    memberIds = memberIds,
    groupIds = groupIds,
    schedule = schedule?.let { TimeRangeDto(it.start?.atOffset(ZoneOffset.UTC), it.due?.atOffset(ZoneOffset.UTC)) },
    tags = tags,
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC),
    schemaVersion = schemaVersion
)

fun Membership.toResponse(): MembershipResponse = MembershipResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    projectId = projectId,
    userId = userId,
    role = role.name,
    joinedAt = joinedAt.atOffset(ZoneOffset.UTC)
)
