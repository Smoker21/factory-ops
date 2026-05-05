package com.factoryops.unit.dto

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.Severity
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.interfaces.dto.ChangeActionRequestStatusRequest
import com.factoryops.interfaces.dto.ConvertActionRequestToTaskRequest
import com.factoryops.interfaces.dto.CreateActionRequestRequest
import com.factoryops.interfaces.dto.DispatchActionRequestRequest
import com.factoryops.interfaces.dto.UpdateActionRequestRequest
import com.factoryops.interfaces.dto.toResponse
import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.ZoneOffset

/**
 * Unit tests for ActionRequest DTO validation constraints and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActionRequestDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateActionRequestRequest ──────────────────────────────────────────────

    @Test
    fun `CreateActionRequestRequest happy path passes validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "Emergency Repair Required",
            severityLevel = "HIGH"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateActionRequestRequest with blank originatingOrgId fails validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            originatingOrgId = "",
            targetOrgId = "org-002",
            title = "Test",
            severityLevel = "LOW"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "originatingOrgId" })
    }

    @Test
    fun `CreateActionRequestRequest with blank targetOrgId fails validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            originatingOrgId = "org-001",
            targetOrgId = "",
            title = "Test",
            severityLevel = "LOW"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "targetOrgId" })
    }

    @Test
    fun `CreateActionRequestRequest with blank title fails validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "",
            severityLevel = "LOW"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `CreateActionRequestRequest with title exceeding 200 chars fails validation`() {
        // Given
        val longTitle = "a".repeat(201)
        val dto = CreateActionRequestRequest(
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = longTitle,
            severityLevel = "LOW"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `CreateActionRequestRequest with blank severityLevel fails validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "Test",
            severityLevel = ""
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "severityLevel" })
    }

    @Test
    fun `CreateActionRequestRequest with optional projectId null passes validation`() {
        // Given
        val dto = CreateActionRequestRequest(
            projectId = null,
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "Test",
            severityLevel = "CRITICAL",
            severityReason = "Machine breakdown"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── DispatchActionRequestRequest ────────────────────────────────────────────

    @Test
    fun `DispatchActionRequestRequest happy path passes validation`() {
        // Given
        val dto = DispatchActionRequestRequest(
            title = "Dispatch Test",
            severityLevel = "MEDIUM"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `DispatchActionRequestRequest with blank title fails validation`() {
        // Given
        val dto = DispatchActionRequestRequest(title = "", severityLevel = "LOW")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `DispatchActionRequestRequest with blank severityLevel fails validation`() {
        // Given
        val dto = DispatchActionRequestRequest(title = "Test", severityLevel = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "severityLevel" })
    }

    // ─── ChangeActionRequestStatusRequest ────────────────────────────────────────

    @Test
    fun `ChangeActionRequestStatusRequest happy path passes validation`() {
        // Given
        val dto = ChangeActionRequestStatusRequest(status = "TRIAGED")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ChangeActionRequestStatusRequest with blank status fails validation`() {
        // Given
        val dto = ChangeActionRequestStatusRequest(status = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "status" })
    }

    // ─── ConvertActionRequestToTaskRequest ───────────────────────────────────────

    @Test
    fun `ConvertActionRequestToTaskRequest happy path passes validation`() {
        // Given
        val dto = ConvertActionRequestToTaskRequest(
            projectId = "proj-001",
            taskType = "GENERAL",
            taskTitle = "New Task from AR"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ConvertActionRequestToTaskRequest with blank projectId fails validation`() {
        // Given
        val dto = ConvertActionRequestToTaskRequest(projectId = "", taskType = "GENERAL", taskTitle = "Test")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "projectId" })
    }

    @Test
    fun `ConvertActionRequestToTaskRequest with blank taskType fails validation`() {
        // Given
        val dto = ConvertActionRequestToTaskRequest(projectId = "proj-001", taskType = "", taskTitle = "Test")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "taskType" })
    }

    @Test
    fun `ConvertActionRequestToTaskRequest with blank taskTitle fails validation`() {
        // Given
        val dto = ConvertActionRequestToTaskRequest(projectId = "proj-001", taskType = "GENERAL", taskTitle = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "taskTitle" })
    }

    // ─── UpdateActionRequestRequest (all optional) ────────────────────────────────

    @Test
    fun `UpdateActionRequestRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateActionRequestRequest()

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── ActionRequest.toResponse() ──────────────────────────────────────────────

    @Test
    fun `ActionRequest toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val ar = ActionRequest(
            id = "ar-001",
            rootOrgId = "root-001",
            projectId = "proj-001",
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "Emergency Repair",
            descriptionMarkdown = "## Urgent",
            severity = Severity(level = SeverityLevel.CRITICAL, reason = "Machine stopped"),
            status = ActionRequestStatus.SUBMITTED,
            requesterId = "user-001",
            ownerId = "user-002",
            linkedTaskId = null,
            attachments = emptyList(),
            history = emptyList(),
            submittedAt = now,
            resolvedAt = null,
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val response = ar.toResponse()

        // Then
        assertEquals("ar-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("proj-001", response.projectId)
        assertEquals("org-001", response.originatingOrgId)
        assertEquals("org-002", response.targetOrgId)
        assertEquals("Emergency Repair", response.title)
        assertEquals("## Urgent", response.descriptionMarkdown)
        assertEquals("CRITICAL", response.severity.level)
        assertEquals("Machine stopped", response.severity.reason)
        assertEquals("SUBMITTED", response.status)
        assertEquals("user-001", response.requesterId)
        assertEquals("user-002", response.ownerId)
        assertNull(response.linkedTaskId)
        assertNull(response.resolvedAt)
        assertEquals(1, response.schemaVersion)
    }

    @Test
    fun `ActionRequest toResponse with null optional fields`() {
        // Given
        val now = Instant.now()
        val ar = ActionRequest(
            id = "ar-002",
            rootOrgId = "root-001",
            projectId = null,
            originatingOrgId = "org-001",
            targetOrgId = "org-002",
            title = "No Project AR",
            severity = Severity(level = SeverityLevel.LOW, reason = null),
            status = ActionRequestStatus.RESOLVED,
            requesterId = "user-001",
            ownerId = null,
            linkedTaskId = "task-001",
            submittedAt = now,
            resolvedAt = now.plusSeconds(3600),
            createdAt = now,
            updatedAt = now
        )

        // When
        val response = ar.toResponse()

        // Then
        assertNull(response.projectId)
        assertNull(response.ownerId)
        assertNull(response.severity.reason)
        assertEquals("task-001", response.linkedTaskId)
        assertEquals(now.plusSeconds(3600).atOffset(ZoneOffset.UTC), response.resolvedAt)
    }
}
