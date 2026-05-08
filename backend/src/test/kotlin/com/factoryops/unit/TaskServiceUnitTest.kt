package com.factoryops.unit

import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.TaskService
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.QaReviewDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import com.factoryops.persistence.repository.UserRepository
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.UserDocument
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for TaskService business rules (no CDI / MongoDB).
 * Uses mockito-kotlin to stub repositories.
 */
class TaskServiceUnitTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var userRepository: UserRepository
    private lateinit var eventPublisher: EventPublisherService
    private lateinit var taskService: TaskService

    private val rootId = ObjectId()
    private val projectId = ObjectId()
    private val ownerId = ObjectId()
    private val taskId = ObjectId()

    @BeforeEach
    fun setup() {
        taskRepository = mock()
        projectRepository = mock()
        groupRepository = mock()
        groupMembershipRepository = mock()
        userRepository = mock()
        eventPublisher = mock()
        taskService = TaskService(taskRepository, projectRepository, groupRepository, groupMembershipRepository, userRepository, eventPublisher)
    }

    // ─── Helper builders ───────────────────────────────────────────────────────

    private fun makeProjectDoc(groupIds: List<ObjectId> = emptyList()): ProjectDocument {
        val doc = ProjectDocument()
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.name = "Test Project"
        doc.status = "ACTIVE"
        doc.ownerId = ownerId
        doc.memberIds = listOf(ownerId)
        doc.groupIds = groupIds
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeGroupDocWithQa(dualSign: Boolean, roles: List<String> = emptyList()): GroupDocument {
        val gid = ObjectId()
        val doc = GroupDocument()
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.type = "SHIFT"
        doc.name = "Test Group"
        doc.code = "test-grp"
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = dualSign
                q.requiredReviewerRoles = roles
            }
        }
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTaskDoc(
        status: TaskStatus = TaskStatus.OPEN,
        ownerIdParam: ObjectId = ownerId,
        dualSign: Boolean = false,
        requiredRoles: List<String> = emptyList()
    ): TaskDocument {
        val doc = TaskDocument()
        doc.id = taskId  // Must set id so TaskMapper.toDomain doesn't NPE on doc.id!!
        doc.rootOrgId = rootId
        doc.projectId = projectId
        doc.type = "GENERAL"
        doc.title = "Test"
        doc.status = status.name
        doc.priority = Priority.NORMAL.name
        doc.ownerId = ownerIdParam
        doc.assignees = listOf(ownerIdParam)
        doc.qaReviewPolicy = QaReviewPolicyDocument().also { p ->
            p.dualSignRequired = dualSign
            p.requiredReviewerRoles = requiredRoles
        }
        doc.history = emptyList()
        doc.createdBy = ownerIdParam
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    // ─── INV-1: ownerId must be in assignees ────────────────────────────────

    @Test
    fun `should include ownerId in assignees even when not provided explicitly`() {
        // Given
        val assigneeId = ObjectId()
        val projectDoc = makeProjectDoc()
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        whenever(groupRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)
        val capturedDoc = argumentCaptor<TaskDocument>()
        // Use doAnswer to assign id so TaskMapper.toDomain can call doc.id!! after persist
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<TaskDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(capturedDoc.capture())
        whenever(eventPublisher.publishTaskCreated(any(), any())).then {}

        // When
        taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "GENERAL",
            title = "Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = ownerId.toHexString(),
            assignees = listOf(assigneeId.toHexString()),
            attributes = emptyMap(),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: ownerId must be in assignees (INV-1)
        val persisted = capturedDoc.firstValue
        assertTrue(persisted.assignees.contains(ownerId), "ownerId must be in assignees list")
    }

    // ─── INV-2: addAssignees appends history entry ────────────────────────────

    @Test
    fun `should append TASK_ASSIGNEES_ADDED history entry when addAssignees called`() {
        // Given
        val taskDoc = makeTaskDoc()
        val newUserId = ObjectId()
        val activeUserDoc = UserDocument().also { u ->
            u.id = newUserId; u.rootOrgId = rootId; u.accountName = "user1"
            u.employeeNo = "E001"; u.displayName = "User 1"; u.active = true
            u.createdAt = Instant.now()
        }
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        // C-012: mock user lookup to return active user
        whenever(userRepository.findByIdAndNotDeleted(any(), eq(rootId))).thenReturn(activeUserDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskAssigned(any(), any())).then {}

        // When
        taskService.addAssignees(taskId.toHexString(), rootId.toHexString(), listOf(newUserId.toHexString()), ownerId.toHexString())

        // Then
        val updated = captured.firstValue
        assertTrue(updated.history.any { it.action == "TASK_ASSIGNEES_ADDED" }, "History must contain TASK_ASSIGNEES_ADDED")
    }

    // ─── INV-3: transferOwner appends history entry ───────────────────────────

    @Test
    fun `should append TASK_OWNER_TRANSFERRED history entry on transferTaskOwner`() {
        // Given
        val newOwner = ObjectId()
        val taskDoc = makeTaskDoc()
        taskDoc.assignees = listOf(ownerId, newOwner)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskOwnerTransferred(any(), any())).then {}

        // When
        taskService.transferTaskOwner(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newOwnerId = newOwner.toHexString(),
            keepPreviousOwnerAsAssignee = true,
            reason = "shift change",
            actorId = ownerId.toHexString()
        )

        // Then
        val updated = captured.firstValue
        assertEquals(newOwner, updated.ownerId, "ownerId must be updated to new owner")
        assertTrue(updated.history.any { it.action == "TASK_OWNER_TRANSFERRED" }, "History must contain TASK_OWNER_TRANSFERRED")
    }

    // ─── INV-3b: removeAssignee appends history entry ────────────────────────

    @Test
    fun `should append TASK_ASSIGNEE_REMOVED history entry on removeAssignee`() {
        // Given
        val extraAssignee = ObjectId()
        val taskDoc = makeTaskDoc()
        taskDoc.assignees = listOf(ownerId, extraAssignee)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())

        // When
        taskService.removeAssignee(taskId.toHexString(), rootId.toHexString(), extraAssignee.toHexString(), ownerId.toHexString())

        // Then
        val updated = captured.firstValue
        assertTrue(updated.history.any { it.action == "TASK_ASSIGNEE_REMOVED" }, "History must contain TASK_ASSIGNEE_REMOVED")
    }

    // ─── INV-6: Status flow OPEN → IN_PROGRESS → DONE path ──────────────────

    @Test
    fun `should allow OPEN to IN_PROGRESS transition`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.OPEN)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())

        // When
        val result = taskService.changeTaskStatus(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = TaskStatus.IN_PROGRESS,
            reason = null,
            actorRoles = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(TaskStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `should reject OPEN to DONE transition as invalid`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.OPEN)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(StateTransitionException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.DONE,
                reason = null,
                actorRoles = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject DONE to OPEN transition as terminal state`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.DONE)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(StateTransitionException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.OPEN,
                reason = null,
                actorRoles = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject CANCELLED to IN_PROGRESS transition as terminal state`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.CANCELLED)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(StateTransitionException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.IN_PROGRESS,
                reason = null,
                actorRoles = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    // ─── Soft-delete: deletedAt is set, document is not removed ──────────────

    @Test
    fun `should soft-delete task by setting deletedAt instead of removing document`() {
        // Given
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())

        // When
        taskService.deleteTask(taskId.toHexString(), rootId.toHexString(), ownerId.toHexString())

        // Then: update called (not delete), deletedAt set
        verify(taskRepository, never()).delete(any<TaskDocument>())
        assertNotNull(captured.firstValue.deletedAt, "deletedAt must be set on soft-delete")
    }

    // ─── QA dual sign: snapshot policy at creation ────────────────────────────

    @Test
    fun `should snapshot QA policy from group at task creation time`() {
        // Given: group has dualSignRequired = true with required roles
        val groupDoc = makeGroupDocWithQa(dualSign = true, roles = listOf("QA", "SHIFT_LEAD"))
        val groupId = ObjectId()
        val projectDoc = makeProjectDoc(groupIds = listOf(groupId))
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        val captured = argumentCaptor<TaskDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<TaskDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(captured.capture())
        whenever(eventPublisher.publishTaskCreated(any(), any())).then {}

        // When
        taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "EQUIPMENT_INSPECTION",
            title = "Inspection",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = ownerId.toHexString(),
            assignees = listOf(ownerId.toHexString()),
            attributes = mapOf("equipmentId" to "EQ-001"),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: policy is snapshotted
        val persisted = captured.firstValue
        assertTrue(persisted.qaReviewPolicy.dualSignRequired, "QA policy dualSignRequired must be snapshotted from group")
        assertTrue(persisted.qaReviewPolicy.requiredReviewerRoles.contains("QA"))
        assertTrue(persisted.qaReviewPolicy.requiredReviewerRoles.contains("SHIFT_LEAD"))
    }

    // ─── QA dual sign: IN_PROGRESS → DONE blocked without GROUP_MANAGER ──────

    @Test
    fun `should reject IN_PROGRESS to DONE when dualSignRequired and actor lacks GROUP_MANAGER role`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_PROGRESS, dualSign = true, requiredRoles = listOf("QA"))
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(BusinessRuleViolationException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.DONE,
                reason = "bypass",
                actorRoles = listOf("SHIFT_LEAD"),  // missing GROUP_MANAGER
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject force-complete without reason when dualSignRequired`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_PROGRESS, dualSign = true, requiredRoles = listOf("QA"))
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        // C-006: reason check comes AFTER membership check, so mock membership as satisfied
        val membershipDoc = GroupMembershipDocument().also { m ->
            m.id = ObjectId(); m.rootOrgId = rootId; m.groupId = ObjectId()
            m.userId = ownerId; m.joinedAt = Instant.now()
        }
        whenever(groupMembershipRepository.findActiveByGroupIdAndUserId(any(), any(), any())).thenReturn(membershipDoc)

        // When / Then
        assertThrows(ValidationException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.DONE,
                reason = null,  // missing required reason
                actorRoles = listOf("GROUP_MANAGER"),
                actorId = ownerId.toHexString()
            )
        }
    }

    // ─── QA review: Q-23 OR auto-complete requires size >= 2 when dualSignRequired ──

    @Test
    fun `should complete task when 2 or more reviews approved with whitelisted roles (Q-23 OR)`() {
        // Given: task IN_REVIEW, dualSignRequired=true, whitelist = [QA, SHIFT_LEAD]
        // Pre-populate with one existing APPROVED review from SHIFT_LEAD
        val existingReview = QaReviewDocument().also { r ->
            r.reviewerId = ObjectId(); r.reviewerRole = "SHIFT_LEAD"
            r.decision = ReviewDecision.APPROVED.name; r.at = Instant.now()
        }
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_REVIEW, dualSign = true, requiredRoles = listOf("QA", "SHIFT_LEAD"))
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: second review (QA) is submitted — now size >= 2, auto-complete triggers
        val result = taskService.submitReview(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            reviewerRole = Role.QA,
            decision = ReviewDecision.APPROVED,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then: task moves to DONE (size >= 2 with all-in-whitelist roles)
        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `should stay IN_REVIEW after first approval when dualSignRequired is true (Q-23 OR)`() {
        // Given: task IN_REVIEW, dualSignRequired=true, only one review submitted
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_REVIEW, dualSign = true, requiredRoles = listOf("QA"))
        // No existing reviews — first review should not complete
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())

        // When
        val result = taskService.submitReview(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            reviewerRole = Role.QA,
            decision = ReviewDecision.APPROVED,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then: task stays IN_REVIEW (need >= 2 for dualSign)
        assertEquals(TaskStatus.IN_REVIEW, result.status)
    }

    @Test
    fun `should revert task to IN_PROGRESS when review is rejected`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_REVIEW, dualSign = true, requiredRoles = listOf("QA"))
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())

        // When
        val result = taskService.submitReview(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            reviewerRole = Role.QA,
            decision = ReviewDecision.REJECTED,
            reason = "Incomplete checklist",
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(TaskStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `should reject review if task is not IN_REVIEW status`() {
        // Given
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_PROGRESS, dualSign = true, requiredRoles = listOf("QA"))
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(ConflictException::class.java) {
            taskService.submitReview(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                reviewerRole = Role.QA,
                decision = ReviewDecision.APPROVED,
                reason = null,
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject review from role not in required reviewer roles`() {
        // Given: required = QA, reviewer is SHIFT_LEAD
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_REVIEW, dualSign = true, requiredRoles = listOf("QA"))
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(ForbiddenException::class.java) {
            taskService.submitReview(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                reviewerRole = Role.SHIFT_LEAD,  // not in required roles
                decision = ReviewDecision.APPROVED,
                reason = null,
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject duplicate review from same reviewer and role`() {
        // Given: task already has QA review from same actor
        val taskDoc = makeTaskDoc(status = TaskStatus.IN_REVIEW, dualSign = true, requiredRoles = listOf("QA", "SHIFT_LEAD"))
        val existingReview = QaReviewDocument().also { r ->
            r.reviewerId = ownerId
            r.reviewerRole = "QA"
            r.decision = "APPROVED"
            r.at = Instant.now()
        }
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then: same actor submitting QA role review again
        assertThrows(ConflictException::class.java) {
            taskService.submitReview(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                reviewerRole = Role.QA,
                decision = ReviewDecision.APPROVED,
                reason = null,
                actorId = ownerId.toHexString()  // same actor
            )
        }
    }

    // ─── removeAssignee: cannot remove owner ─────────────────────────────────

    @Test
    fun `should reject removing task owner from assignees`() {
        // Given
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(ConflictException::class.java) {
            taskService.removeAssignee(taskId.toHexString(), rootId.toHexString(), ownerId.toHexString(), ownerId.toHexString())
        }
    }

    // ─── getTask: throws NotFoundException for missing task ──────────────────

    @Test
    fun `should throw NotFoundException when task not found`() {
        // Given
        whenever(taskRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            taskService.getTask(ObjectId().toHexString(), rootId.toHexString())
        }
    }
}
