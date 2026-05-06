package com.factoryops.interfaces.dto

import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.task.Comment
import com.factoryops.domain.task.QaReview
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class CreateTaskRequest(
    @field:NotBlank(message = "projectId is required")
    val projectId: String = "",

    @field:NotBlank(message = "type is required")
    val type: String = "",

    @field:NotBlank(message = "title is required")
    @field:Size(min = 1, max = 200)
    val title: String = "",

    val descriptionMarkdown: String? = null,
    val priority: String = "NORMAL",

    @field:NotBlank(message = "ownerId is required")
    val ownerId: String = "",

    val assignees: List<String> = emptyList(),
    val attributes: Map<String, Any?> = emptyMap(),
    val schedule: TimeRangeDto? = null,
    val tags: List<String> = emptyList()
)

data class UpdateTaskRequest(
    val title: String? = null,
    val descriptionMarkdown: String? = null,
    val priority: String? = null,
    val attributes: Map<String, Any?>? = null,
    val schedule: TimeRangeDto? = null,
    val tags: List<String>? = null
)

data class ChangeTaskStatusRequest(
    @field:NotBlank(message = "status is required")
    val status: String = "",
    val reason: String? = null
)

data class AddAssigneesRequest(
    @field:NotEmpty(message = "userIds must not be empty")
    val userIds: List<String> = emptyList()
)

data class TransferTaskOwnerRequest(
    @field:NotBlank(message = "newOwnerId is required")
    val newOwnerId: String = "",
    val keepPreviousOwnerAsAssignee: Boolean = true,
    val reason: String? = null
)

data class TaskReviewRequest(
    @field:NotBlank(message = "role is required")
    val role: String = "",

    @field:NotBlank(message = "decision is required")
    val decision: String = "",

    val reason: String? = null
)

data class CreateCommentRequest(
    @field:NotBlank(message = "bodyMarkdown is required")
    val bodyMarkdown: String = ""
)

data class EditCommentRequest(
    @field:NotBlank(message = "bodyMarkdown is required")
    val bodyMarkdown: String = ""
)

data class TaskResponse(
    val id: String,
    val rootOrgId: String,
    val projectId: String,
    val type: String,
    val title: String,
    val descriptionMarkdown: String?,
    val status: String,
    val priority: String,
    val ownerId: String,
    val assignees: List<String>,
    val attributes: Map<String, Any?>,
    val schedule: TimeRangeDto?,
    val completedAt: OffsetDateTime?,
    val originActionRequestId: String?,
    val qaReviewPolicy: QaReviewPolicyResponse,
    val qaReviews: List<QaReviewResponse>,
    val tags: List<String>,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    val schemaVersion: Int
)

data class QaReviewPolicyResponse(
    val dualSignRequired: Boolean,
    val requiredReviewerRoles: List<String>,
    val snapshotAt: OffsetDateTime,
    val sourceGroupIds: List<String>
)

data class QaReviewResponse(
    val reviewerId: String,
    val reviewerRole: String,
    val decision: String,
    val reason: String?,
    val at: OffsetDateTime
)

data class CommentResponse(
    val id: String,
    val authorId: String,
    val bodyMarkdown: String,
    val createdAt: OffsetDateTime,
    val editedAt: OffsetDateTime?,
    val deletedAt: OffsetDateTime?
)

data class TaskPageResponse(
    val items: List<TaskResponse>,
    val pageInfo: PageInfo
)

data class TaskTypeDefinition(
    val type: String,
    val description: String,
    val attributesSchema: Map<String, Any?> = emptyMap()
)

fun Task.toResponse(): TaskResponse = TaskResponse(
    id = id!!,
    rootOrgId = rootOrgId,
    projectId = projectId,
    type = type,
    title = title,
    descriptionMarkdown = descriptionMarkdown,
    status = status.name,
    priority = priority.name,
    ownerId = ownerId,
    assignees = assignees,
    attributes = attributes,
    schedule = schedule?.let { TimeRangeDto(it.start?.atOffset(ZoneOffset.UTC), it.due?.atOffset(ZoneOffset.UTC)) },
    completedAt = completedAt?.atOffset(ZoneOffset.UTC),
    originActionRequestId = originActionRequestId,
    qaReviewPolicy = qaReviewPolicy.toResponse(),
    qaReviews = qaReviews.map { it.toResponse() },
    tags = tags,
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    createdBy = createdBy,
    updatedAt = updatedAt.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC),
    schemaVersion = schemaVersion
)

fun QaReviewPolicy.toResponse(): QaReviewPolicyResponse = QaReviewPolicyResponse(
    dualSignRequired = dualSignRequired,
    requiredReviewerRoles = requiredReviewerRoles.map { it.name },
    snapshotAt = snapshotAt.atOffset(ZoneOffset.UTC),
    sourceGroupIds = sourceGroupIds
)

fun QaReview.toResponse(): QaReviewResponse = QaReviewResponse(
    reviewerId = reviewerId,
    reviewerRole = reviewerRole.name,
    decision = decision.name,
    reason = reason,
    at = at.atOffset(ZoneOffset.UTC)
)

fun Comment.toResponse(): CommentResponse = CommentResponse(
    id = id,
    authorId = authorId,
    bodyMarkdown = bodyMarkdown,
    createdAt = createdAt.atOffset(ZoneOffset.UTC),
    editedAt = editedAt?.atOffset(ZoneOffset.UTC),
    deletedAt = deletedAt?.atOffset(ZoneOffset.UTC)
)
