package com.factoryops.interfaces.dto

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.SeverityLevel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CreateActionRequestRequest(
    val projectId: String? = null,

    @field:NotBlank(message = "originatingOrgId is required")
    val originatingOrgId: String = "",

    @field:NotBlank(message = "targetOrgId is required")
    val targetOrgId: String = "",

    @field:NotBlank(message = "title is required")
    @field:Size(min = 1, max = 200)
    val title: String = "",

    val descriptionMarkdown: String? = null,

    @field:NotBlank(message = "severityLevel is required")
    val severityLevel: String = "LOW",

    val severityReason: String? = null
)

data class DispatchActionRequestRequest(
    @field:NotBlank(message = "title is required")
    @field:Size(min = 1, max = 200)
    val title: String = "",

    val descriptionMarkdown: String? = null,

    @field:NotBlank(message = "severityLevel is required")
    val severityLevel: String = "LOW",

    val severityReason: String? = null,

    val ownerId: String? = null
)

data class UpdateActionRequestRequest(
    val title: String? = null,
    val descriptionMarkdown: String? = null
)

data class ChangeActionRequestStatusRequest(
    @field:NotBlank(message = "status is required")
    val status: String = "",
    val reason: String? = null
)

data class ConvertActionRequestToTaskRequest(
    @field:NotBlank(message = "projectId is required")
    val projectId: String = "",

    @field:NotBlank(message = "taskType is required")
    val taskType: String = "",

    @field:NotBlank(message = "taskTitle is required")
    val taskTitle: String = ""
)

data class ActionRequestResponse(
    val id: String,
    val rootOrgId: String,
    val projectId: String?,
    val originatingOrgId: String,
    val targetOrgId: String,
    val title: String,
    val descriptionMarkdown: String?,
    val severity: SeverityResponse,
    val status: String,
    val requesterId: String,
    val ownerId: String?,
    val linkedTaskId: String?,
    val submittedAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val schemaVersion: Int
)

data class SeverityResponse(
    val level: String,
    val reason: String?
)

data class ActionRequestPageResponse(
    val items: List<ActionRequestResponse>,
    val pageInfo: PageInfo
)

fun ActionRequest.toResponse(): ActionRequestResponse = ActionRequestResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    projectId = projectId,
    originatingOrgId = originatingOrgId,
    targetOrgId = targetOrgId,
    title = title,
    descriptionMarkdown = descriptionMarkdown,
    severity = SeverityResponse(severity.level.name, severity.reason),
    status = status.name,
    requesterId = requesterId,
    ownerId = ownerId,
    linkedTaskId = linkedTaskId,
    submittedAt = submittedAt.atOffset(ZoneOffset.UTC),
    resolvedAt = resolvedAt?.atOffset(ZoneOffset.UTC),
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    schemaVersion = schemaVersion
)
