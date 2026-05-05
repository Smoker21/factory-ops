package com.factoryops.unit.mapper

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
import com.factoryops.persistence.mapper.TaskMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for TaskMapper — Document ↔ Domain round-trips.
 */
class TaskMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val projectId = ObjectId()
        val ownerId = ObjectId()
        val assigneeId = ObjectId()
        val groupId = ObjectId()
        val reviewerId = ObjectId()
        val createdById = ObjectId()
        val originArId = ObjectId()
        val now = Instant.now()

        val doc = TaskDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.projectId = projectId
        doc.type = "EQUIPMENT_INSPECTION"
        doc.title = "CNC-M3 Weekly Inspection"
        doc.descriptionMarkdown = "## Checklist"
        doc.status = "IN_REVIEW"
        doc.priority = "HIGH"
        doc.ownerId = ownerId
        doc.assignees = listOf(ownerId, assigneeId)
        doc.attributes = mapOf("equipmentId" to "CNC-M3")
        val attachRef = AttachmentRefDocument()
        attachRef.attachmentId = "att-001"
        attachRef.alt = "Evidence"
        attachRef.role = "INLINE"
        doc.attachments = listOf(attachRef)
        doc.schedule = TimeRangeDocument().also { t ->
            t.start = now.minusSeconds(7200)
            t.due = now.plusSeconds(86400)
        }
        doc.completedAt = null
        doc.originActionRequestId = originArId
        val templateRef = TemplateRefDocument()
        templateRef.templateType = "TASK_TEMPLATE"
        templateRef.templateScope = "ORG"
        templateRef.templateId = "tmpl-001"
        templateRef.templateVersion = 1
        templateRef.instantiatedAt = now
        doc.templateRef = templateRef
        val policyDoc = QaReviewPolicyDocument()
        policyDoc.dualSignRequired = true
        policyDoc.requiredReviewerRoles = listOf("QA", "SHIFT_LEAD")
        policyDoc.snapshotAt = now
        policyDoc.sourceGroupIds = listOf(groupId)
        doc.qaReviewPolicy = policyDoc
        val reviewDoc = QaReviewDocument()
        reviewDoc.reviewerId = reviewerId
        reviewDoc.reviewerRole = "QA"
        reviewDoc.decision = "APPROVED"
        reviewDoc.reason = "Looks good"
        reviewDoc.at = now
        doc.qaReviews = listOf(reviewDoc)
        val commentDoc = CommentDocument()
        commentDoc.id = "comment-001"
        commentDoc.authorId = ownerId
        commentDoc.bodyMarkdown = "Done!"
        commentDoc.createdAt = now
        doc.comments = listOf(commentDoc)
        doc.tags = listOf("equipment", "weekly")
        doc.history = emptyList()
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.createdBy = createdById
        doc.updatedAt = now
        doc.deletedAt = null

        // When
        val domain = TaskMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(projectId.toHexString(), domain.projectId)
        assertEquals("EQUIPMENT_INSPECTION", domain.type)
        assertEquals("CNC-M3 Weekly Inspection", domain.title)
        assertEquals("## Checklist", domain.descriptionMarkdown)
        assertEquals(TaskStatus.IN_REVIEW, domain.status)
        assertEquals(Priority.HIGH, domain.priority)
        assertEquals(ownerId.toHexString(), domain.ownerId)
        assertEquals(2, domain.assignees.size)
        assertEquals("CNC-M3", domain.attributes["equipmentId"])
        assertEquals(1, domain.attachments.size)
        assertEquals("att-001", domain.attachments[0].attachmentId)
        assertEquals(AttachmentRole.INLINE, domain.attachments[0].role)
        assertNotNull(domain.schedule)
        assertEquals(originArId.toHexString(), domain.originActionRequestId)
        assertNotNull(domain.templateRef)
        assertEquals(TemplateScope.ORG, domain.templateRef?.templateScope)
        assertTrue(domain.qaReviewPolicy.dualSignRequired)
        assertEquals(2, domain.qaReviewPolicy.requiredReviewerRoles.size)
        assertEquals(1, domain.qaReviews.size)
        assertEquals(reviewerId.toHexString(), domain.qaReviews[0].reviewerId)
        assertEquals(Role.QA, domain.qaReviews[0].reviewerRole)
        assertEquals(ReviewDecision.APPROVED, domain.qaReviews[0].decision)
        assertEquals("Looks good", domain.qaReviews[0].reason)
        assertEquals(1, domain.comments.size)
        assertEquals("comment-001", domain.comments[0].id)
        assertEquals(listOf("equipment", "weekly"), domain.tags)
        assertNull(domain.deletedAt)
        assertNull(domain.completedAt)
    }

    @Test
    fun `toDomain with unknown status defaults to OPEN`() {
        // Given
        val doc = makeMinimalTaskDoc()
        doc.status = "UNKNOWN_STATUS"

        // When
        val domain = TaskMapper.toDomain(doc)

        // Then
        assertEquals(TaskStatus.OPEN, domain.status)
    }

    @Test
    fun `toDomain with unknown priority defaults to NORMAL`() {
        // Given
        val doc = makeMinimalTaskDoc()
        doc.priority = "UNKNOWN_PRIORITY"

        // When
        val domain = TaskMapper.toDomain(doc)

        // Then
        assertEquals(Priority.NORMAL, domain.priority)
    }

    @Test
    fun `toDomain maps minimal fields with null optionals`() {
        // Given
        val doc = makeMinimalTaskDoc()

        // When
        val domain = TaskMapper.toDomain(doc)

        // Then
        assertNull(domain.descriptionMarkdown)
        assertNull(domain.schedule)
        assertNull(domain.completedAt)
        assertNull(domain.originActionRequestId)
        assertNull(domain.templateRef)
        assertNull(domain.deletedAt)
        assertTrue(domain.attachments.isEmpty())
        assertTrue(domain.qaReviews.isEmpty())
        assertTrue(domain.comments.isEmpty())
        assertTrue(domain.tags.isEmpty())
        assertTrue(domain.history.isEmpty())
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val taskId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val projectId = ObjectId().toHexString()
        val ownerId = ObjectId().toHexString()
        val groupId = ObjectId().toHexString()
        val actorId = ObjectId().toHexString()
        val now = Instant.now()

        val task = Task(
            id = taskId,
            rootOrgId = rootId,
            projectId = projectId,
            type = "SHIFT_HANDOVER",
            title = "Evening Handover",
            descriptionMarkdown = "## Notes",
            status = TaskStatus.IN_PROGRESS,
            priority = Priority.URGENT,
            ownerId = ownerId,
            assignees = listOf(ownerId),
            attributes = mapOf("shiftId" to "shift-001"),
            attachments = listOf(AttachmentRef(attachmentId = "att-x", role = AttachmentRole.ATTACHMENT)),
            schedule = TimeRange(start = now.minusSeconds(3600), due = now.plusSeconds(3600)),
            completedAt = null,
            originActionRequestId = null,
            templateRef = TemplateRef(
                templateType = "TASK_TEMPLATE",
                templateScope = TemplateScope.ORG,
                templateId = "tmpl-002",
                templateVersion = 1,
                instantiatedAt = now
            ),
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = false,
                requiredReviewerRoles = emptyList(),
                snapshotAt = now,
                sourceGroupIds = listOf(groupId)
            ),
            qaReviews = emptyList(),
            comments = listOf(Comment(
                id = "cmt-001",
                authorId = ownerId,
                bodyMarkdown = "Handover complete",
                createdAt = now
            )),
            tags = listOf("handover"),
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            createdBy = actorId,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val doc = TaskMapper.toDocument(task)

        // Then
        assertEquals(ObjectId(taskId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(projectId), doc.projectId)
        assertEquals("SHIFT_HANDOVER", doc.type)
        assertEquals("Evening Handover", doc.title)
        assertEquals("IN_PROGRESS", doc.status)
        assertEquals("URGENT", doc.priority)
        assertEquals(ObjectId(ownerId), doc.ownerId)
        assertEquals(1, doc.assignees.size)
        assertEquals("shift-001", doc.attributes["shiftId"])
        assertEquals(1, doc.attachments.size)
        assertNotNull(doc.schedule)
        assertNotNull(doc.templateRef)
        assertEquals("tmpl-002", doc.templateRef?.templateId)
        assertEquals(false, doc.qaReviewPolicy.dualSignRequired)
        assertEquals(1, doc.comments.size)
        assertEquals("cmt-001", doc.comments[0].id)
        assertEquals(listOf("handover"), doc.tags)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val task = makeMinimalTask(id = null)

        // When
        val doc = TaskMapper.toDocument(task)

        // Then
        assertNull(doc.id)
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves key fields`() {
        // Given
        val taskId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val now = Instant.now()

        val original = makeMinimalTask(id = taskId, rootId = rootId, now = now)

        // When
        val roundTripped = TaskMapper.toDomain(TaskMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.type, roundTripped.type)
        assertEquals(original.title, roundTripped.title)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.priority, roundTripped.priority)
        assertEquals(original.ownerId, roundTripped.ownerId)
        assertEquals(original.schemaVersion, roundTripped.schemaVersion)
    }

    // ─── Helper builders ──────────────────────────────────────────────────────

    private fun makeMinimalTaskDoc(): TaskDocument {
        val doc = TaskDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.projectId = ObjectId()
        doc.type = "GENERAL"
        doc.title = "Minimal Task"
        doc.status = "OPEN"
        doc.priority = "NORMAL"
        doc.ownerId = ObjectId()
        doc.assignees = listOf(doc.ownerId)
        doc.createdAt = Instant.now()
        doc.createdBy = doc.ownerId
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeMinimalTask(
        id: String? = ObjectId().toHexString(),
        rootId: String = ObjectId().toHexString(),
        now: Instant = Instant.now()
    ): Task {
        val ownerId = ObjectId().toHexString()
        return Task(
            id = id,
            rootOrgId = rootId,
            projectId = ObjectId().toHexString(),
            type = "GENERAL",
            title = "Minimal Task",
            status = TaskStatus.OPEN,
            priority = Priority.NORMAL,
            ownerId = ownerId,
            assignees = listOf(ownerId),
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = false,
                requiredReviewerRoles = emptyList(),
                snapshotAt = now
            ),
            schemaVersion = 1,
            createdAt = now,
            createdBy = ownerId,
            updatedAt = now
        )
    }
}
