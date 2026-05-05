package com.factoryops.persistence.mapper

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.Severity
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.AttachmentRef
import com.factoryops.domain.shared.AttachmentRole
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.AttachmentRefDocument
import com.factoryops.persistence.document.SeverityDocument
import org.bson.types.ObjectId

object ActionRequestMapper {

    fun toDomain(doc: ActionRequestDocument): ActionRequest = ActionRequest(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        projectId = doc.projectId?.toHexString(),
        originatingOrgId = doc.originatingOrgId.toHexString(),
        targetOrgId = doc.targetOrgId.toHexString(),
        title = doc.title,
        descriptionMarkdown = doc.descriptionMarkdown,
        severity = Severity(
            level = runCatching { SeverityLevel.valueOf(doc.severity.level) }.getOrDefault(SeverityLevel.LOW),
            reason = doc.severity.reason
        ),
        status = runCatching { ActionRequestStatus.valueOf(doc.status) }.getOrDefault(ActionRequestStatus.SUBMITTED),
        requesterId = doc.requesterId.toHexString(),
        ownerId = doc.ownerId?.toHexString(),
        linkedTaskId = doc.linkedTaskId?.toHexString(),
        attachments = doc.attachments.map { ref ->
            AttachmentRef(
                attachmentId = ref.attachmentId,
                alt = ref.alt,
                role = runCatching { AttachmentRole.valueOf(ref.role) }.getOrDefault(AttachmentRole.ATTACHMENT)
            )
        },
        history = doc.history.map { OrganizationMapper.auditToDomain(it) },
        submittedAt = doc.submittedAt,
        resolvedAt = doc.resolvedAt,
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        updatedAt = doc.updatedAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(ar: ActionRequest): ActionRequestDocument {
        val doc = ActionRequestDocument()
        ar.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(ar.rootOrgId)
        doc.projectId = ar.projectId?.let { ObjectId(it) }
        doc.originatingOrgId = ObjectId(ar.originatingOrgId)
        doc.targetOrgId = ObjectId(ar.targetOrgId)
        doc.title = ar.title
        doc.descriptionMarkdown = ar.descriptionMarkdown
        doc.severity = SeverityDocument().also { s -> s.level = ar.severity.level.name; s.reason = ar.severity.reason }
        doc.status = ar.status.name
        doc.requesterId = ObjectId(ar.requesterId)
        doc.ownerId = ar.ownerId?.let { ObjectId(it) }
        doc.linkedTaskId = ar.linkedTaskId?.let { ObjectId(it) }
        doc.attachments = ar.attachments.map { ref ->
            AttachmentRefDocument().also { d -> d.attachmentId = ref.attachmentId; d.alt = ref.alt; d.role = ref.role.name }
        }
        doc.history = ar.history.map { OrganizationMapper.auditToDocument(it) }
        doc.submittedAt = ar.submittedAt
        doc.resolvedAt = ar.resolvedAt
        doc.schemaVersion = ar.schemaVersion
        doc.createdAt = ar.createdAt
        doc.updatedAt = ar.updatedAt
        doc.deletedAt = ar.deletedAt
        return doc
    }
}
