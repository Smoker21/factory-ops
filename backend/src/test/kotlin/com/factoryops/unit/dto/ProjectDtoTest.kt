package com.factoryops.unit.dto

import com.factoryops.domain.membership.Membership
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.Project
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.TimeRange
import com.factoryops.interfaces.dto.AddProjectMemberRequest
import com.factoryops.interfaces.dto.ChangeProjectOwnerRequest
import com.factoryops.interfaces.dto.ChangeProjectStatusRequest
import com.factoryops.interfaces.dto.CreateProjectRequest
import com.factoryops.interfaces.dto.TimeRangeDto
import com.factoryops.interfaces.dto.UpdateProjectRequest
import com.factoryops.interfaces.dto.toResponse
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Unit tests for Project DTO validation and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateProjectRequest ────────────────────────────────────────────────────

    @Test
    fun `CreateProjectRequest happy path passes validation`() {
        // Given
        val dto = CreateProjectRequest(
            organizationId = "org123",
            name = "Test Project",
            ownerId = "user123",
            groupIds = listOf("group1")
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateProjectRequest with blank organizationId fails validation`() {
        // Given
        val dto = CreateProjectRequest(organizationId = "", name = "Test Project", ownerId = "user123", groupIds = listOf("g1"))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "organizationId" })
    }

    @Test
    fun `CreateProjectRequest with blank name fails validation`() {
        // Given
        val dto = CreateProjectRequest(organizationId = "org123", name = "", ownerId = "user123", groupIds = listOf("g1"))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateProjectRequest with name exceeding 120 chars fails validation`() {
        // Given
        val longName = "a".repeat(121)
        val dto = CreateProjectRequest(organizationId = "org123", name = longName, ownerId = "user123", groupIds = listOf("g1"))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "name" })
    }

    @Test
    fun `CreateProjectRequest with blank ownerId fails validation`() {
        // Given
        val dto = CreateProjectRequest(organizationId = "org123", name = "Test Project", ownerId = "", groupIds = listOf("g1"))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "ownerId" })
    }

    @Test
    fun `CreateProjectRequest with empty groupIds fails validation`() {
        // Given
        val dto = CreateProjectRequest(organizationId = "org123", name = "Test Project", ownerId = "user123", groupIds = emptyList())

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "groupIds" })
    }

    // ─── ChangeProjectStatusRequest ─────────────────────────────────────────────

    @Test
    fun `ChangeProjectStatusRequest happy path passes validation`() {
        // Given
        val dto = ChangeProjectStatusRequest(status = "ACTIVE")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ChangeProjectStatusRequest with blank status fails validation`() {
        // Given
        val dto = ChangeProjectStatusRequest(status = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "status" })
    }

    // ─── ChangeProjectOwnerRequest ──────────────────────────────────────────────

    @Test
    fun `ChangeProjectOwnerRequest happy path passes validation`() {
        // Given
        val dto = ChangeProjectOwnerRequest(newOwnerId = "newowner123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ChangeProjectOwnerRequest with blank newOwnerId fails validation`() {
        // Given
        val dto = ChangeProjectOwnerRequest(newOwnerId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "newOwnerId" })
    }

    // ─── AddProjectMemberRequest ─────────────────────────────────────────────────

    @Test
    fun `AddProjectMemberRequest happy path passes validation`() {
        // Given
        val dto = AddProjectMemberRequest(userId = "user123", role = "MEMBER")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `AddProjectMemberRequest with blank userId fails validation`() {
        // Given
        val dto = AddProjectMemberRequest(userId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "userId" })
    }

    // ─── UpdateProjectRequest ────────────────────────────────────────────────────

    @Test
    fun `UpdateProjectRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateProjectRequest()

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── TimeRangeDto toDomain ───────────────────────────────────────────────────

    @Test
    fun `TimeRangeDto toDomain with both times set`() {
        // Given
        val start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
        val due = OffsetDateTime.now(ZoneOffset.UTC).plusDays(7)
        val dto = TimeRangeDto(start = start, due = due)

        // When
        val domain = dto.toDomain()

        // Then
        assertEquals(start.toInstant(), domain.start)
        assertEquals(due.toInstant(), domain.due)
    }

    @Test
    fun `TimeRangeDto toDomain with null times`() {
        // Given
        val dto = TimeRangeDto(start = null, due = null)

        // When
        val domain = dto.toDomain()

        // Then
        assertNull(domain.start)
        assertNull(domain.due)
    }

    // ─── Project.toResponse() ────────────────────────────────────────────────────

    @Test
    fun `Project toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val project = Project(
            id = "proj-001",
            rootOrgId = "root-001",
            organizationId = "org-001",
            name = "Test Project",
            descriptionMarkdown = "## Description",
            status = ProjectStatus.ACTIVE,
            ownerId = "owner-001",
            memberIds = listOf("owner-001", "member-002"),
            groupIds = listOf("group-001"),
            schedule = null,
            tags = listOf("tag1", "tag2"),
            templateRef = null,
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val response = project.toResponse()

        // Then
        assertEquals("proj-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("org-001", response.organizationId)
        assertEquals("Test Project", response.name)
        assertEquals("## Description", response.descriptionMarkdown)
        assertEquals("ACTIVE", response.status)
        assertEquals("owner-001", response.ownerId)
        assertEquals(listOf("owner-001", "member-002"), response.memberIds)
        assertEquals(listOf("group-001"), response.groupIds)
        assertEquals(listOf("tag1", "tag2"), response.tags)
        assertEquals(1, response.schemaVersion)
        assertNull(response.schedule)
        assertNull(response.deletedAt)
    }

    @Test
    fun `Project toResponse with schedule maps correctly`() {
        // Given
        val now = Instant.now()
        val start = now.minusSeconds(3600)
        val due = now.plusSeconds(3600)
        val project = Project(
            id = "proj-002",
            rootOrgId = "root-001",
            organizationId = "org-001",
            name = "Scheduled Project",
            status = ProjectStatus.DRAFT,
            ownerId = "owner-001",
            schedule = TimeRange(start = start, due = due),
            createdAt = now,
            updatedAt = now
        )

        // When
        val response = project.toResponse()

        // Then
        assertNotNull(response.schedule)
        assertEquals(start.atOffset(ZoneOffset.UTC), response.schedule?.start)
        assertEquals(due.atOffset(ZoneOffset.UTC), response.schedule?.due)
    }

    @Test
    fun `Project toResponse with deletedAt maps correctly`() {
        // Given
        val now = Instant.now()
        val deletedAt = now.minusSeconds(60)
        val project = Project(
            id = "proj-003",
            rootOrgId = "root-001",
            organizationId = "org-001",
            name = "Deleted Project",
            status = ProjectStatus.CANCELLED,
            ownerId = "owner-001",
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt
        )

        // When
        val response = project.toResponse()

        // Then
        assertEquals(deletedAt.atOffset(ZoneOffset.UTC), response.deletedAt)
    }

    // ─── Membership.toResponse() ─────────────────────────────────────────────────

    @Test
    fun `Membership toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val membership = Membership(
            id = "mem-001",
            rootOrgId = "root-001",
            projectId = "proj-001",
            userId = "user-001",
            role = MembershipRole.LEAD,
            joinedAt = now,
            schemaVersion = 1
        )

        // When
        val response = membership.toResponse()

        // Then
        assertEquals("mem-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("proj-001", response.projectId)
        assertEquals("user-001", response.userId)
        assertEquals("LEAD", response.role)
        assertEquals(now.atOffset(ZoneOffset.UTC), response.joinedAt)
    }
}
