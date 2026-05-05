package com.factoryops.unit.dto

import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.task.Comment
import com.factoryops.domain.task.QaReview
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.dto.AddAssigneesRequest
import com.factoryops.interfaces.dto.ChangeTaskStatusRequest
import com.factoryops.interfaces.dto.CreateCommentRequest
import com.factoryops.interfaces.dto.CreateTaskRequest
import com.factoryops.interfaces.dto.EditCommentRequest
import com.factoryops.interfaces.dto.TaskReviewRequest
import com.factoryops.interfaces.dto.TransferTaskOwnerRequest
import com.factoryops.interfaces.dto.UpdateTaskRequest
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

/**
 * Unit tests for Task DTO validation constraints and domain-to-response converters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaskDtoTest {

    private lateinit var validator: Validator

    @BeforeAll
    fun setup() {
        validator = Validation.buildDefaultValidatorFactory().validator
    }

    // ─── CreateTaskRequest ──────────────────────────────────────────────────────

    @Test
    fun `CreateTaskRequest happy path passes validation`() {
        // Given
        val dto = CreateTaskRequest(
            projectId = "abc123",
            type = "GENERAL",
            title = "Test task",
            ownerId = "user123"
        )

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateTaskRequest with blank projectId fails validation`() {
        // Given
        val dto = CreateTaskRequest(projectId = "", type = "GENERAL", title = "Test", ownerId = "user123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "projectId" })
    }

    @Test
    fun `CreateTaskRequest with blank type fails validation`() {
        // Given
        val dto = CreateTaskRequest(projectId = "proj123", type = "", title = "Test", ownerId = "user123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "type" })
    }

    @Test
    fun `CreateTaskRequest with blank title fails validation`() {
        // Given
        val dto = CreateTaskRequest(projectId = "proj123", type = "GENERAL", title = "", ownerId = "user123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `CreateTaskRequest with title exceeding 200 chars fails validation`() {
        // Given
        val longTitle = "a".repeat(201)
        val dto = CreateTaskRequest(projectId = "proj123", type = "GENERAL", title = longTitle, ownerId = "user123")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `CreateTaskRequest with blank ownerId fails validation`() {
        // Given
        val dto = CreateTaskRequest(projectId = "proj123", type = "GENERAL", title = "Test", ownerId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "ownerId" })
    }

    // ─── ChangeTaskStatusRequest ────────────────────────────────────────────────

    @Test
    fun `ChangeTaskStatusRequest happy path passes validation`() {
        // Given
        val dto = ChangeTaskStatusRequest(status = "IN_PROGRESS")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `ChangeTaskStatusRequest with blank status fails validation`() {
        // Given
        val dto = ChangeTaskStatusRequest(status = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "status" })
    }

    // ─── AddAssigneesRequest ────────────────────────────────────────────────────

    @Test
    fun `AddAssigneesRequest happy path passes validation`() {
        // Given
        val dto = AddAssigneesRequest(userIds = listOf("user1", "user2"))

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `AddAssigneesRequest with empty list fails validation`() {
        // Given
        val dto = AddAssigneesRequest(userIds = emptyList())

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "userIds" })
    }

    // ─── TransferTaskOwnerRequest ────────────────────────────────────────────────

    @Test
    fun `TransferTaskOwnerRequest happy path passes validation`() {
        // Given
        val dto = TransferTaskOwnerRequest(newOwnerId = "newuser123", keepPreviousOwnerAsAssignee = true)

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `TransferTaskOwnerRequest with blank newOwnerId fails validation`() {
        // Given
        val dto = TransferTaskOwnerRequest(newOwnerId = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "newOwnerId" })
    }

    // ─── TaskReviewRequest ───────────────────────────────────────────────────────

    @Test
    fun `TaskReviewRequest happy path passes validation`() {
        // Given
        val dto = TaskReviewRequest(role = "QA", decision = "APPROVED")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `TaskReviewRequest with blank role fails validation`() {
        // Given
        val dto = TaskReviewRequest(role = "", decision = "APPROVED")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "role" })
    }

    @Test
    fun `TaskReviewRequest with blank decision fails validation`() {
        // Given
        val dto = TaskReviewRequest(role = "QA", decision = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "decision" })
    }

    // ─── CreateCommentRequest ────────────────────────────────────────────────────

    @Test
    fun `CreateCommentRequest happy path passes validation`() {
        // Given
        val dto = CreateCommentRequest(bodyMarkdown = "# This is a comment")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `CreateCommentRequest with blank body fails validation`() {
        // Given
        val dto = CreateCommentRequest(bodyMarkdown = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "bodyMarkdown" })
    }

    // ─── EditCommentRequest ─────────────────────────────────────────────────────

    @Test
    fun `EditCommentRequest with blank body fails validation`() {
        // Given
        val dto = EditCommentRequest(bodyMarkdown = "")

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.any { it.propertyPath.toString() == "bodyMarkdown" })
    }

    // ─── UpdateTaskRequest (no required fields, all nullable) ──────────────────

    @Test
    fun `UpdateTaskRequest with all nulls passes validation`() {
        // Given
        val dto = UpdateTaskRequest()

        // When
        val violations = validator.validate(dto)

        // Then
        assertTrue(violations.isEmpty())
    }

    // ─── Task.toResponse() extension function ───────────────────────────────────

    @Test
    fun `Task toResponse maps all fields correctly`() {
        // Given
        val now = Instant.now()
        val task = Task(
            id = "task-id-001",
            rootOrgId = "root-001",
            projectId = "proj-001",
            type = "GENERAL",
            title = "My Task",
            descriptionMarkdown = "## Description",
            status = TaskStatus.IN_PROGRESS,
            priority = Priority.HIGH,
            ownerId = "owner-001",
            assignees = listOf("owner-001", "user-002"),
            attributes = mapOf("key" to "value"),
            schedule = null,
            completedAt = null,
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = false,
                requiredReviewerRoles = emptyList(),
                snapshotAt = now,
                sourceGroupIds = emptyList()
            ),
            qaReviews = emptyList(),
            comments = emptyList(),
            tags = listOf("tag1"),
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            createdBy = "creator-001",
            updatedAt = now,
            deletedAt = null
        )

        // When
        val response = task.toResponse()

        // Then
        assertEquals("task-id-001", response.id)
        assertEquals("root-001", response.rootOrgId)
        assertEquals("proj-001", response.projectId)
        assertEquals("GENERAL", response.type)
        assertEquals("My Task", response.title)
        assertEquals("## Description", response.descriptionMarkdown)
        assertEquals("IN_PROGRESS", response.status)
        assertEquals("HIGH", response.priority)
        assertEquals("owner-001", response.ownerId)
        assertEquals(listOf("owner-001", "user-002"), response.assignees)
        assertEquals(listOf("tag1"), response.tags)
        assertEquals(1, response.schemaVersion)
        assertNull(response.deletedAt)
        assertNull(response.completedAt)
    }

    @Test
    fun `Task toResponse with qaReviews maps review fields`() {
        // Given
        val now = Instant.now()
        val review = QaReview(
            reviewerId = "reviewer-001",
            reviewerRole = Role.QA,
            decision = ReviewDecision.APPROVED,
            reason = "Looks good",
            at = now
        )
        val task = Task(
            id = "task-id-002",
            rootOrgId = "root-001",
            projectId = "proj-001",
            type = "GENERAL",
            title = "Task With Reviews",
            status = TaskStatus.IN_REVIEW,
            priority = Priority.NORMAL,
            ownerId = "owner-001",
            assignees = listOf("owner-001"),
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = true,
                requiredReviewerRoles = listOf(Role.QA),
                snapshotAt = now,
                sourceGroupIds = listOf("group-001")
            ),
            qaReviews = listOf(review),
            comments = emptyList(),
            tags = emptyList(),
            history = emptyList(),
            createdAt = now,
            createdBy = "owner-001",
            updatedAt = now
        )

        // When
        val response = task.toResponse()

        // Then
        assertEquals(1, response.qaReviews.size)
        assertEquals("reviewer-001", response.qaReviews[0].reviewerId)
        assertEquals("QA", response.qaReviews[0].reviewerRole)
        assertEquals("APPROVED", response.qaReviews[0].decision)
        assertEquals("Looks good", response.qaReviews[0].reason)
        assertTrue(response.qaReviewPolicy.dualSignRequired)
        assertEquals(listOf("QA"), response.qaReviewPolicy.requiredReviewerRoles)
        assertEquals(listOf("group-001"), response.qaReviewPolicy.sourceGroupIds)
    }

    @Test
    fun `Comment toResponse maps fields correctly`() {
        // Given
        val now = Instant.now()
        val comment = Comment(
            id = "comment-001",
            authorId = "user-001",
            bodyMarkdown = "## This is a comment",
            attachments = emptyList(),
            createdAt = now,
            editedAt = null,
            deletedAt = null
        )

        // When
        val response = comment.toResponse()

        // Then
        assertEquals("comment-001", response.id)
        assertEquals("user-001", response.authorId)
        assertEquals("## This is a comment", response.bodyMarkdown)
        assertNull(response.editedAt)
        assertNull(response.deletedAt)
    }
}
