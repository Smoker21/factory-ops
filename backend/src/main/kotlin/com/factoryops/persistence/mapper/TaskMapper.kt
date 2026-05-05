package com.factoryops.persistence.mapper

import com.factoryops.domain.shared.AttachmentRef
import com.factoryops.domain.shared.AttachmentRole
import com.factoryops.domain.shared.TemplateRef
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.shared.enums.TemplateScope
import com.factoryops.domain.task.Comment
import com.factoryops.domain.task.QaReview
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.persistence.document.AttachmentRefDocument
import com.factoryops.persistence.document.CommentDocument
import com.factoryops.persistence.document.QaReviewDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.document.TemplateRefDocument
import com.factoryops.persistence.document.TimeRangeDocument
import org.bson.types.ObjectId

object TaskMapper {

    fun toDomain(doc: TaskDocument): Task = Task(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        projectId = doc.projectId.toHexString(),
        type = doc.type,
        title = doc.title,
        descriptionMarkdown = doc.descriptionMarkdown,
        status = runCatching { TaskStatus.valueOf(doc.status) }.getOrDefault(TaskStatus.OPEN),
        priority = runCatching { Priority.valueOf(doc.priority) }.getOrDefault(Priority.NORMAL),
        ownerId = doc.ownerId.toHexString(),
        assignees = doc.assignees.map { it.toHexString() },
        attributes = doc.attributes,
        attachments = doc.attachments.map { attachmentRefToDomain(it) },
        schedule = doc.schedule?.let { TimeRange(it.start, it.due) },
        completedAt = doc.completedAt,
        originActionRequestId = doc.originActionRequestId?.toHexString(),
        templateRef = doc.templateRef?.let { templateRefToDomain(it) },
        qaReviewPolicy = qaReviewPolicyToDomain(doc.qaReviewPolicy),
        qaReviews = doc.qaReviews.map { qaReviewToDomain(it) },
        comments = doc.comments.map { commentToDomain(it) },
        tags = doc.tags,
        history = doc.history.map { OrganizationMapper.auditToDomain(it) },
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        createdBy = doc.createdBy.toHexString(),
        updatedAt = doc.updatedAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(task: Task): TaskDocument {
        val doc = TaskDocument()
        task.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(task.rootOrgId)
        doc.projectId = ObjectId(task.projectId)
        doc.type = task.type
        doc.title = task.title
        doc.descriptionMarkdown = task.descriptionMarkdown
        doc.status = task.status.name
        doc.priority = task.priority.name
        doc.ownerId = ObjectId(task.ownerId)
        doc.assignees = task.assignees.map { ObjectId(it) }
        doc.attributes = task.attributes
        doc.attachments = task.attachments.map { attachmentRefToDocument(it) }
        doc.schedule = task.schedule?.let { TimeRangeDocument().also { d -> d.start = it.start; d.due = it.due } }
        doc.completedAt = task.completedAt
        doc.originActionRequestId = task.originActionRequestId?.let { ObjectId(it) }
        doc.templateRef = task.templateRef?.let { templateRefToDocument(it) }
        doc.qaReviewPolicy = qaReviewPolicyToDocument(task.qaReviewPolicy)
        doc.qaReviews = task.qaReviews.map { qaReviewToDocument(it) }
        doc.comments = task.comments.map { commentToDocument(it) }
        doc.tags = task.tags
        doc.history = task.history.map { OrganizationMapper.auditToDocument(it) }
        doc.schemaVersion = task.schemaVersion
        doc.createdAt = task.createdAt
        doc.createdBy = ObjectId(task.createdBy)
        doc.updatedAt = task.updatedAt
        doc.deletedAt = task.deletedAt
        return doc
    }

    private fun attachmentRefToDomain(doc: AttachmentRefDocument): AttachmentRef = AttachmentRef(
        attachmentId = doc.attachmentId,
        alt = doc.alt,
        role = runCatching { AttachmentRole.valueOf(doc.role) }.getOrDefault(AttachmentRole.ATTACHMENT)
    )

    private fun attachmentRefToDocument(ref: AttachmentRef): AttachmentRefDocument {
        val doc = AttachmentRefDocument()
        doc.attachmentId = ref.attachmentId
        doc.alt = ref.alt
        doc.role = ref.role.name
        return doc
    }

    private fun qaReviewPolicyToDomain(doc: QaReviewPolicyDocument): QaReviewPolicy = QaReviewPolicy(
        dualSignRequired = doc.dualSignRequired,
        requiredReviewerRoles = doc.requiredReviewerRoles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() },
        snapshotAt = doc.snapshotAt,
        sourceGroupIds = doc.sourceGroupIds.map { it.toHexString() }
    )

    private fun qaReviewPolicyToDocument(policy: QaReviewPolicy): QaReviewPolicyDocument {
        val doc = QaReviewPolicyDocument()
        doc.dualSignRequired = policy.dualSignRequired
        doc.requiredReviewerRoles = policy.requiredReviewerRoles.map { it.name }
        doc.snapshotAt = policy.snapshotAt
        doc.sourceGroupIds = policy.sourceGroupIds.map { ObjectId(it) }
        return doc
    }

    private fun qaReviewToDomain(doc: QaReviewDocument): QaReview = QaReview(
        reviewerId = doc.reviewerId.toHexString(),
        reviewerRole = runCatching { Role.valueOf(doc.reviewerRole) }.getOrDefault(Role.QA),
        decision = runCatching { ReviewDecision.valueOf(doc.decision) }.getOrDefault(ReviewDecision.APPROVED),
        reason = doc.reason,
        at = doc.at
    )

    private fun qaReviewToDocument(review: QaReview): QaReviewDocument {
        val doc = QaReviewDocument()
        doc.reviewerId = ObjectId(review.reviewerId)
        doc.reviewerRole = review.reviewerRole.name
        doc.decision = review.decision.name
        doc.reason = review.reason
        doc.at = review.at
        return doc
    }

    private fun commentToDomain(doc: CommentDocument): Comment = Comment(
        id = doc.id,
        authorId = doc.authorId.toHexString(),
        bodyMarkdown = doc.bodyMarkdown,
        attachments = doc.attachments.map { attachmentRefToDomain(it) },
        createdAt = doc.createdAt,
        editedAt = doc.editedAt,
        deletedAt = doc.deletedAt
    )

    private fun commentToDocument(comment: Comment): CommentDocument {
        val doc = CommentDocument()
        doc.id = comment.id
        doc.authorId = ObjectId(comment.authorId)
        doc.bodyMarkdown = comment.bodyMarkdown
        doc.attachments = comment.attachments.map { attachmentRefToDocument(it) }
        doc.createdAt = comment.createdAt
        doc.editedAt = comment.editedAt
        doc.deletedAt = comment.deletedAt
        return doc
    }

    private fun templateRefToDomain(doc: TemplateRefDocument): TemplateRef = TemplateRef(
        templateType = doc.templateType,
        templateScope = runCatching { TemplateScope.valueOf(doc.templateScope) }.getOrDefault(TemplateScope.ORG),
        templateId = doc.templateId,
        templateVersion = doc.templateVersion,
        instantiatedAt = doc.instantiatedAt
    )

    private fun templateRefToDocument(ref: TemplateRef): TemplateRefDocument {
        val doc = TemplateRefDocument()
        doc.templateType = ref.templateType
        doc.templateScope = ref.templateScope.name
        doc.templateId = ref.templateId
        doc.templateVersion = ref.templateVersion
        doc.instantiatedAt = ref.instantiatedAt
        return doc
    }
}
