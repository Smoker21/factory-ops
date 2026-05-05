package com.factoryops.unit.mapper

import com.factoryops.domain.membership.Membership
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.Project
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.TemplateRef
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.TemplateScope
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.TemplateRefDocument
import com.factoryops.persistence.document.TimeRangeDocument
import com.factoryops.persistence.mapper.ProjectMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for ProjectMapper — Document ↔ Domain round-trips.
 */
class ProjectMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val orgId = ObjectId()
        val ownerId = ObjectId()
        val member1Id = ObjectId()
        val groupId = ObjectId()
        val now = Instant.now()
        val start = now.minusSeconds(3600)
        val due = now.plusSeconds(86400)

        val doc = ProjectDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.name = "Test Project"
        doc.descriptionMarkdown = "## Desc"
        doc.status = "ACTIVE"
        doc.ownerId = ownerId
        doc.memberIds = listOf(ownerId, member1Id)
        doc.groupIds = listOf(groupId)
        doc.schedule = TimeRangeDocument().also { t -> t.start = start; t.due = due }
        doc.tags = listOf("tag1", "tag2")
        val templateRef = TemplateRefDocument()
        templateRef.templateType = "TASK_TEMPLATE"
        templateRef.templateScope = "ORG"
        templateRef.templateId = "tmpl-001"
        templateRef.templateVersion = 2
        templateRef.instantiatedAt = now
        doc.templateRef = templateRef
        doc.history = emptyList()
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.updatedAt = now
        doc.deletedAt = null

        // When
        val domain = ProjectMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(orgId.toHexString(), domain.organizationId)
        assertEquals("Test Project", domain.name)
        assertEquals("## Desc", domain.descriptionMarkdown)
        assertEquals(ProjectStatus.ACTIVE, domain.status)
        assertEquals(ownerId.toHexString(), domain.ownerId)
        assertEquals(listOf(ownerId.toHexString(), member1Id.toHexString()), domain.memberIds)
        assertEquals(listOf(groupId.toHexString()), domain.groupIds)
        assertNotNull(domain.schedule)
        assertEquals(start, domain.schedule?.start)
        assertEquals(due, domain.schedule?.due)
        assertEquals(listOf("tag1", "tag2"), domain.tags)
        assertNotNull(domain.templateRef)
        assertEquals("TASK_TEMPLATE", domain.templateRef?.templateType)
        assertEquals(TemplateScope.ORG, domain.templateRef?.templateScope)
        assertEquals("tmpl-001", domain.templateRef?.templateId)
        assertEquals(2, domain.templateRef?.templateVersion)
        assertEquals(1, domain.schemaVersion)
        assertNull(domain.deletedAt)
    }

    @Test
    fun `toDomain maps with unknown status defaults to DRAFT`() {
        // Given
        val doc = ProjectDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.organizationId = ObjectId()
        doc.name = "Unknown Status Project"
        doc.status = "UNKNOWN_STATUS"
        doc.ownerId = ObjectId()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = ProjectMapper.toDomain(doc)

        // Then
        assertEquals(ProjectStatus.DRAFT, domain.status)
    }

    @Test
    fun `toDomain maps minimal fields with null optionals`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val orgId = ObjectId()
        val ownerId = ObjectId()
        val now = Instant.now()

        val doc = ProjectDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.name = "Minimal Project"
        doc.status = "DRAFT"
        doc.ownerId = ownerId
        doc.createdAt = now
        doc.updatedAt = now

        // When
        val domain = ProjectMapper.toDomain(doc)

        // Then
        assertNull(domain.descriptionMarkdown)
        assertNull(domain.schedule)
        assertNull(domain.templateRef)
        assertNull(domain.deletedAt)
        assertTrue(domain.memberIds.isEmpty())
        assertTrue(domain.groupIds.isEmpty())
        assertTrue(domain.tags.isEmpty())
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val projId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val orgId = ObjectId().toHexString()
        val ownerId = ObjectId().toHexString()
        val memberId = ObjectId().toHexString()
        val groupId = ObjectId().toHexString()
        val actorId = ObjectId().toHexString()
        val now = Instant.now()

        val project = Project(
            id = projId,
            rootOrgId = rootId,
            organizationId = orgId,
            name = "Full Project",
            descriptionMarkdown = "## Full",
            status = ProjectStatus.PAUSED,
            ownerId = ownerId,
            memberIds = listOf(ownerId, memberId),
            groupIds = listOf(groupId),
            schedule = TimeRange(start = now.minusSeconds(3600), due = now.plusSeconds(86400)),
            tags = listOf("important"),
            templateRef = TemplateRef(
                templateType = "PROJECT_TEMPLATE",
                templateScope = TemplateScope.ORG,
                templateId = "pt-001",
                templateVersion = 1,
                instantiatedAt = now
            ),
            history = listOf(AuditEntry(actorId = actorId, action = "PROJECT_CREATED", at = now)),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val doc = ProjectMapper.toDocument(project)

        // Then
        assertEquals(ObjectId(projId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(orgId), doc.organizationId)
        assertEquals("Full Project", doc.name)
        assertEquals("PAUSED", doc.status)
        assertEquals(ObjectId(ownerId), doc.ownerId)
        assertEquals(2, doc.memberIds.size)
        assertEquals(1, doc.groupIds.size)
        assertNotNull(doc.schedule)
        assertNotNull(doc.templateRef)
        assertEquals("PROJECT_TEMPLATE", doc.templateRef?.templateType)
        assertEquals(1, doc.history.size)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val rootId = ObjectId().toHexString()
        val orgId = ObjectId().toHexString()
        val ownerId = ObjectId().toHexString()
        val now = Instant.now()

        val project = Project(
            id = null,
            rootOrgId = rootId,
            organizationId = orgId,
            name = "New Project",
            status = ProjectStatus.DRAFT,
            ownerId = ownerId,
            createdAt = now,
            updatedAt = now
        )

        // When
        val doc = ProjectMapper.toDocument(project)

        // Then
        assertNull(doc.id)
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves key fields`() {
        // Given
        val projId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val orgId = ObjectId().toHexString()
        val ownerId = ObjectId().toHexString()
        val now = Instant.now()
        val deletedAt = now.minusSeconds(60)

        val original = Project(
            id = projId,
            rootOrgId = rootId,
            organizationId = orgId,
            name = "Round-Trip Project",
            status = ProjectStatus.COMPLETED,
            ownerId = ownerId,
            tags = listOf("a", "b"),
            schemaVersion = 3,
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt
        )

        // When
        val roundTripped = ProjectMapper.toDomain(ProjectMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.tags, roundTripped.tags)
        assertEquals(original.schemaVersion, roundTripped.schemaVersion)
        assertEquals(original.deletedAt, roundTripped.deletedAt)
    }

    // ─── membershipToDomain ─────────────────────────────────────────────────────

    @Test
    fun `membershipToDomain maps all fields correctly`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val projId = ObjectId()
        val userId = ObjectId()
        val now = Instant.now()

        val doc = MembershipDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.projectId = projId
        doc.userId = userId
        doc.role = "LEAD"
        doc.joinedAt = now
        doc.schemaVersion = 1

        // When
        val domain = ProjectMapper.membershipToDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(projId.toHexString(), domain.projectId)
        assertEquals(userId.toHexString(), domain.userId)
        assertEquals(MembershipRole.LEAD, domain.role)
        assertEquals(now, domain.joinedAt)
    }

    @Test
    fun `membershipToDomain with unknown role defaults to MEMBER`() {
        // Given
        val doc = MembershipDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.projectId = ObjectId()
        doc.userId = ObjectId()
        doc.role = "UNKNOWN_ROLE"
        doc.joinedAt = Instant.now()

        // When
        val domain = ProjectMapper.membershipToDomain(doc)

        // Then
        assertEquals(MembershipRole.MEMBER, domain.role)
    }

    // ─── membershipToDocument ───────────────────────────────────────────────────

    @Test
    fun `membershipToDocument maps all fields correctly`() {
        // Given
        val memId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val projId = ObjectId().toHexString()
        val userId = ObjectId().toHexString()
        val now = Instant.now()

        val membership = Membership(
            id = memId,
            rootOrgId = rootId,
            projectId = projId,
            userId = userId,
            role = MembershipRole.OBSERVER,
            joinedAt = now,
            schemaVersion = 1
        )

        // When
        val doc = ProjectMapper.membershipToDocument(membership)

        // Then
        assertEquals(ObjectId(memId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(projId), doc.projectId)
        assertEquals(ObjectId(userId), doc.userId)
        assertEquals("OBSERVER", doc.role)
        assertEquals(now, doc.joinedAt)
    }

    @Test
    fun `membershipToDocument with null id does not set id`() {
        // Given
        val rootId = ObjectId().toHexString()
        val projId = ObjectId().toHexString()
        val userId = ObjectId().toHexString()

        val membership = Membership(
            id = null,
            rootOrgId = rootId,
            projectId = projId,
            userId = userId,
            joinedAt = Instant.now()
        )

        // When
        val doc = ProjectMapper.membershipToDocument(membership)

        // Then
        assertNull(doc.id)
    }
}
