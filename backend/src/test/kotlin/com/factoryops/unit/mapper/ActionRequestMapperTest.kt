package com.factoryops.unit.mapper

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.Severity
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.AttachmentRef
import com.factoryops.domain.shared.AttachmentRole
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.AttachmentRefDocument
import com.factoryops.persistence.document.AuditEntryDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.mapper.ActionRequestMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for ActionRequestMapper — Document ↔ Domain round-trips.
 */
class ActionRequestMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val projectId = ObjectId()
        val originOrgId = ObjectId()
        val targetOrgId = ObjectId()
        val requesterId = ObjectId()
        val ownerId = ObjectId()
        val linkedTaskId = ObjectId()
        val actorId = ObjectId()
        val now = Instant.now()

        val doc = ActionRequestDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.projectId = projectId
        doc.originatingOrgId = originOrgId
        doc.targetOrgId = targetOrgId
        doc.title = "Emergency Repair"
        doc.descriptionMarkdown = "## Urgent"
        doc.severity = SeverityDocument().also { s ->
            s.level = "CRITICAL"
            s.reason = "Machine breakdown"
        }
        doc.status = "TRIAGED"
        doc.requesterId = requesterId
        doc.ownerId = ownerId
        doc.linkedTaskId = linkedTaskId
        val attachment = AttachmentRefDocument()
        attachment.attachmentId = "att-001"
        attachment.alt = "Photo"
        attachment.role = "INLINE"
        doc.attachments = listOf(attachment)
        val auditDoc = AuditEntryDocument()
        auditDoc.actorId = actorId
        auditDoc.action = "AR_CREATED"
        auditDoc.at = now
        auditDoc.payload = emptyMap()
        doc.history = listOf(auditDoc)
        doc.submittedAt = now
        doc.resolvedAt = now.plusSeconds(3600)
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.updatedAt = now
        doc.deletedAt = null

        // When
        val domain = ActionRequestMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(projectId.toHexString(), domain.projectId)
        assertEquals(originOrgId.toHexString(), domain.originatingOrgId)
        assertEquals(targetOrgId.toHexString(), domain.targetOrgId)
        assertEquals("Emergency Repair", domain.title)
        assertEquals("## Urgent", domain.descriptionMarkdown)
        assertEquals(SeverityLevel.CRITICAL, domain.severity.level)
        assertEquals("Machine breakdown", domain.severity.reason)
        assertEquals(ActionRequestStatus.TRIAGED, domain.status)
        assertEquals(requesterId.toHexString(), domain.requesterId)
        assertEquals(ownerId.toHexString(), domain.ownerId)
        assertEquals(linkedTaskId.toHexString(), domain.linkedTaskId)
        assertEquals(1, domain.attachments.size)
        assertEquals("att-001", domain.attachments[0].attachmentId)
        assertEquals("Photo", domain.attachments[0].alt)
        assertEquals(AttachmentRole.INLINE, domain.attachments[0].role)
        assertEquals(1, domain.history.size)
        assertEquals("AR_CREATED", domain.history[0].action)
        assertEquals(now.plusSeconds(3600), domain.resolvedAt)
        assertNull(domain.deletedAt)
    }

    @Test
    fun `toDomain maps minimal fields with null optionals`() {
        // Given
        val doc = ActionRequestDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.projectId = null
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "Minimal AR"
        doc.severity = SeverityDocument().also { s ->
            s.level = "LOW"
            s.reason = null
        }
        doc.status = "SUBMITTED"
        doc.requesterId = ObjectId()
        doc.ownerId = null
        doc.linkedTaskId = null
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = ActionRequestMapper.toDomain(doc)

        // Then
        assertNull(domain.projectId)
        assertNull(domain.ownerId)
        assertNull(domain.linkedTaskId)
        assertNull(domain.severity.reason)
        assertNull(domain.resolvedAt)
        assertNull(domain.deletedAt)
        assertTrue(domain.attachments.isEmpty())
        assertTrue(domain.history.isEmpty())
    }

    @Test
    fun `toDomain with unknown status defaults to SUBMITTED`() {
        // Given
        val doc = ActionRequestDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "Unknown Status AR"
        doc.severity = SeverityDocument().also { s -> s.level = "LOW" }
        doc.status = "UNKNOWN_STATUS"
        doc.requesterId = ObjectId()
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = ActionRequestMapper.toDomain(doc)

        // Then
        assertEquals(ActionRequestStatus.SUBMITTED, domain.status)
    }

    @Test
    fun `toDomain with unknown severity defaults to LOW`() {
        // Given
        val doc = ActionRequestDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "Unknown Severity AR"
        doc.severity = SeverityDocument().also { s -> s.level = "UNKNOWN_SEVERITY" }
        doc.status = "SUBMITTED"
        doc.requesterId = ObjectId()
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = ActionRequestMapper.toDomain(doc)

        // Then
        assertEquals(SeverityLevel.LOW, domain.severity.level)
    }

    @Test
    fun `toDomain with unknown attachment role defaults to ATTACHMENT`() {
        // Given
        val doc = ActionRequestDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "AR with bad attachment role"
        doc.severity = SeverityDocument().also { s -> s.level = "LOW" }
        doc.status = "SUBMITTED"
        doc.requesterId = ObjectId()
        val badAttachment = AttachmentRefDocument()
        badAttachment.attachmentId = "att-002"
        badAttachment.role = "UNKNOWN_ROLE"
        doc.attachments = listOf(badAttachment)
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = ActionRequestMapper.toDomain(doc)

        // Then
        assertEquals(AttachmentRole.ATTACHMENT, domain.attachments[0].role)
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val arId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val projectId = ObjectId().toHexString()
        val originOrgId = ObjectId().toHexString()
        val targetOrgId = ObjectId().toHexString()
        val requesterId = ObjectId().toHexString()
        val ownerId = ObjectId().toHexString()
        val linkedTaskId = ObjectId().toHexString()
        val actorId = ObjectId().toHexString()
        val now = Instant.now()

        val ar = ActionRequest(
            id = arId,
            rootOrgId = rootId,
            projectId = projectId,
            originatingOrgId = originOrgId,
            targetOrgId = targetOrgId,
            title = "Full AR",
            descriptionMarkdown = "## Desc",
            severity = Severity(level = SeverityLevel.HIGH, reason = "Critical failure"),
            status = ActionRequestStatus.RESOLVED,
            requesterId = requesterId,
            ownerId = ownerId,
            linkedTaskId = linkedTaskId,
            attachments = listOf(AttachmentRef(attachmentId = "att-003", alt = "Evidence", role = AttachmentRole.ATTACHMENT)),
            history = listOf(AuditEntry(actorId = actorId, action = "AR_RESOLVED", at = now)),
            submittedAt = now,
            resolvedAt = now.plusSeconds(7200),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val doc = ActionRequestMapper.toDocument(ar)

        // Then
        assertEquals(ObjectId(arId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(projectId), doc.projectId)
        assertEquals(ObjectId(originOrgId), doc.originatingOrgId)
        assertEquals(ObjectId(targetOrgId), doc.targetOrgId)
        assertEquals("Full AR", doc.title)
        assertEquals("HIGH", doc.severity.level)
        assertEquals("Critical failure", doc.severity.reason)
        assertEquals("RESOLVED", doc.status)
        assertEquals(ObjectId(requesterId), doc.requesterId)
        assertEquals(ObjectId(ownerId), doc.ownerId)
        assertEquals(ObjectId(linkedTaskId), doc.linkedTaskId)
        assertEquals(1, doc.attachments.size)
        assertEquals("att-003", doc.attachments[0].attachmentId)
        assertEquals("ATTACHMENT", doc.attachments[0].role)
        assertEquals(1, doc.history.size)
        assertEquals(now.plusSeconds(7200), doc.resolvedAt)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val ar = ActionRequest(
            id = null,
            rootOrgId = ObjectId().toHexString(),
            originatingOrgId = ObjectId().toHexString(),
            targetOrgId = ObjectId().toHexString(),
            title = "New AR",
            severity = Severity(level = SeverityLevel.LOW),
            requesterId = ObjectId().toHexString(),
            submittedAt = Instant.now(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // When
        val doc = ActionRequestMapper.toDocument(ar)

        // Then
        assertNull(doc.id)
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves key fields`() {
        // Given
        val arId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val now = Instant.now()

        val original = ActionRequest(
            id = arId,
            rootOrgId = rootId,
            originatingOrgId = ObjectId().toHexString(),
            targetOrgId = ObjectId().toHexString(),
            title = "Round-Trip AR",
            severity = Severity(level = SeverityLevel.MEDIUM, reason = "Test"),
            status = ActionRequestStatus.IN_PROGRESS,
            requesterId = ObjectId().toHexString(),
            submittedAt = now,
            schemaVersion = 2,
            createdAt = now,
            updatedAt = now
        )

        // When
        val roundTripped = ActionRequestMapper.toDomain(ActionRequestMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.severity.level, roundTripped.severity.level)
        assertEquals(original.severity.reason, roundTripped.severity.reason)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.schemaVersion, roundTripped.schemaVersion)
    }
}
