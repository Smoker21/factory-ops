package com.factoryops.unit

import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.TaskService
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.QaReviewDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*
import java.time.Instant

/**
 * M5.4.2 invariant coverage for TaskService.
 * Covers: C-006, C-007, C-008, C-011, C-012, and Q-23 OR full test matrix.
 */
class TaskServiceM5InvariantTest {

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
        taskService = TaskService(
            taskRepository, projectRepository, groupRepository,
            groupMembershipRepository, userRepository, eventPublisher
        )
    }

    // ─── Helper builders ──────────────────────────────────────────────────────

    private fun makeProjectDoc(
        groupIds: List<ObjectId> = emptyList(),
        startAt: Instant? = null
    ): ProjectDocument {
        val doc = ProjectDocument()
        doc.id = projectId
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.name = "Test Project"
        doc.status = "ACTIVE"
        doc.ownerId = ownerId
        doc.memberIds = listOf(ownerId)
        doc.groupIds = groupIds
        doc.schedule = if (startAt != null) {
            com.factoryops.persistence.document.TimeRangeDocument().also { it.start = startAt; it.due = null }
        } else null
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeGroupDocWithQa(
        id: ObjectId = ObjectId(),
        dualSign: Boolean = false,
        roles: List<String> = emptyList()
    ): GroupDocument {
        val doc = GroupDocument()
        doc.id = id
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
        dualSign: Boolean = false,
        requiredRoles: List<String> = emptyList(),
        sourceGroupIds: List<ObjectId> = emptyList()
    ): TaskDocument {
        val doc = TaskDocument()
        doc.id = taskId
        doc.rootOrgId = rootId
        doc.projectId = projectId
        doc.type = "GENERAL"
        doc.title = "Test"
        doc.status = status.name
        doc.priority = Priority.NORMAL.name
        doc.ownerId = ownerId
        doc.assignees = listOf(ownerId)
        doc.qaReviewPolicy = QaReviewPolicyDocument().also { p ->
            p.dualSignRequired = dualSign
            p.requiredReviewerRoles = requiredRoles
            p.sourceGroupIds = sourceGroupIds
        }
        doc.history = emptyList()
        doc.createdBy = ownerId
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeActiveUserDoc(id: ObjectId = ObjectId(), orgRootId: ObjectId = rootId): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = orgRootId
        doc.accountName = "user.${id.toHexString().take(6)}"
        doc.employeeNo = "EMP${id.toHexString().take(4)}"
        doc.displayName = "Test User"
        doc.active = true
        doc.roles = emptyList()
        doc.groupIds = emptyList()
        doc.orgManagerScopes = emptyList()
        doc.createdAt = Instant.now()
        return doc
    }

    private fun makeInactiveUserDoc(id: ObjectId = ObjectId()): UserDocument {
        val doc = makeActiveUserDoc(id)
        doc.active = false
        return doc
    }

    private fun makeMembershipDoc(groupId: ObjectId, userId: ObjectId): GroupMembershipDocument {
        val doc = GroupMembershipDocument()
        doc.id = ObjectId()
        doc.rootOrgId = rootId
        doc.groupId = groupId
        doc.userId = userId
        doc.joinedAt = Instant.now()
        return doc
    }

    private fun makeReviewDoc(reviewerId: ObjectId, role: String, decision: String = "APPROVED"): QaReviewDocument {
        val doc = QaReviewDocument()
        doc.reviewerId = reviewerId
        doc.reviewerRole = role
        doc.decision = decision
        doc.at = Instant.now()
        return doc
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-006: TaskService.forceComplete group membership check
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-006 should reject force-complete when actor is not a group member`() {
        // Given: task requires dualSign, actor has GROUP_MANAGER role but is NOT a group member
        val groupId = ObjectId()
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_PROGRESS,
            dualSign = true,
            requiredRoles = listOf("QA"),
            sourceGroupIds = listOf(groupId)
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        // groupMembershipRepository returns null → actor is NOT a member
        whenever(
            groupMembershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(ownerId))
        ).thenReturn(null)

        // When / Then
        assertThrows(ForbiddenException::class.java) {
            taskService.changeTaskStatus(
                taskId = taskId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.DONE,
                reason = "bypass reason",
                actorRoles = listOf("GROUP_MANAGER"),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `C-006 should allow force-complete when actor is an active group member`() {
        // Given: actor IS a member of one of the task's groups
        val groupId = ObjectId()
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_PROGRESS,
            dualSign = true,
            requiredRoles = listOf("QA"),
            sourceGroupIds = listOf(groupId)
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(
            groupMembershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(ownerId))
        ).thenReturn(makeMembershipDoc(groupId, ownerId))
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When
        val result = taskService.changeTaskStatus(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = TaskStatus.DONE,
            reason = "bypass: QA on leave",
            actorRoles = listOf("GROUP_MANAGER"),
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `C-006 should allow force-complete when actor is member of at least one of multiple groups`() {
        // Given: task has two source groups; actor is member of only the second one
        val groupId1 = ObjectId()
        val groupId2 = ObjectId()
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_PROGRESS,
            dualSign = true,
            requiredRoles = listOf("QA"),
            sourceGroupIds = listOf(groupId1, groupId2)
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(
            groupMembershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId1), eq(ownerId))
        ).thenReturn(null)
        whenever(
            groupMembershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId2), eq(ownerId))
        ).thenReturn(makeMembershipDoc(groupId2, ownerId))
        doNothing().whenever(taskRepository).update(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When
        val result = taskService.changeTaskStatus(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = TaskStatus.DONE,
            reason = "bypass",
            actorRoles = listOf("GROUP_MANAGER"),
            actorId = ownerId.toHexString()
        )

        // Then: success because member of group2
        assertEquals(TaskStatus.DONE, result.status)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-007: buildQaReviewPolicy — missing group throws NotFoundException
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-007 should throw NotFoundException when a groupId in project is not found`() {
        // Given: project has groupIds [existingGroupId, missingGroupId]
        val existingGroupId = ObjectId()
        val missingGroupId = ObjectId()
        val projectDoc = makeProjectDoc(groupIds = listOf(existingGroupId, missingGroupId))
        val groupDoc = makeGroupDocWithQa(id = existingGroupId, dualSign = false)

        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        whenever(groupRepository.findByIdAndRootOrg(eq(existingGroupId), eq(rootId))).thenReturn(groupDoc)
        whenever(groupRepository.findByIdAndRootOrg(eq(missingGroupId), eq(rootId))).thenReturn(null)  // missing!

        // When / Then
        val ex = assertThrows(NotFoundException::class.java) {
            taskService.createTask(
                rootOrgId = rootId.toHexString(),
                projectId = projectId.toHexString(),
                type = "GENERAL",
                title = "Test Task",
                descriptionMarkdown = null,
                priority = Priority.NORMAL,
                ownerId = ownerId.toHexString(),
                assignees = listOf(ownerId.toHexString()),
                attributes = emptyMap(),
                schedule = null,
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
        assertTrue(
            ex.message?.contains(missingGroupId.toHexString()) == true,
            "NotFoundException message must contain the missing group ID"
        )
    }

    @Test
    fun `C-007 should build policy normally when all groups exist`() {
        // Given: all groups found
        val groupId = ObjectId()
        val projectDoc = makeProjectDoc(groupIds = listOf(groupId))
        val groupDoc = makeGroupDocWithQa(id = groupId, dualSign = true, roles = listOf("QA"))
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)
        val captured = argumentCaptor<TaskDocument>()
        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<TaskDocument>(0).id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(captured.capture())
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
            assignees = listOf(ownerId.toHexString()),
            attributes = emptyMap(),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: policy correctly snapshotted
        val persisted = captured.firstValue
        assertTrue(persisted.qaReviewPolicy.dualSignRequired)
        assertTrue(persisted.qaReviewPolicy.requiredReviewerRoles.contains("QA"))
    }

    @Test
    fun `C-007 should build empty policy when project has no groups`() {
        // Given: project has no groupIds (empty list)
        val projectDoc = makeProjectDoc(groupIds = emptyList())
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        val captured = argumentCaptor<TaskDocument>()
        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<TaskDocument>(0).id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(captured.capture())
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
            assignees = listOf(ownerId.toHexString()),
            attributes = emptyMap(),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: no dual sign, empty roles
        val persisted = captured.firstValue
        assertFalse(persisted.qaReviewPolicy.dualSignRequired)
        assertTrue(persisted.qaReviewPolicy.requiredReviewerRoles.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-008: IN_REVIEW → DONE two paths produce consistent history/event
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-008 qa_review_complete path sets DONE status with correct via=qa_review_complete in history`() {
        // Given: task IN_REVIEW, dualSignRequired=true, already has 1 review
        val existingReviewerId = ObjectId()
        val existingReview = makeReviewDoc(existingReviewerId, "SHIFT_LEAD")
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: second review submitted → auto-complete triggers
        taskService.submitReview(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            reviewerRole = Role.QA,
            decision = ReviewDecision.APPROVED,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then: history entry has via=qa_review_complete, bypassed=false
        val updated = captured.firstValue
        assertEquals(TaskStatus.DONE.name, updated.status)
        val historyEntry = updated.history.lastOrNull()
        assertNotNull(historyEntry, "History must have at least one entry")
        assertEquals("TASK_STATUS_CHANGED", historyEntry!!.action)
        assertEquals("qa_review_complete", historyEntry.payload?.get("via"))
        assertEquals(false, historyEntry.payload?.get("bypassed"))
    }

    @Test
    fun `C-008 force_complete path sets DONE status with correct via=force_complete in history`() {
        // Given: task IN_PROGRESS, dualSignRequired=true, actor is GROUP_MANAGER and group member
        val groupId = ObjectId()
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_PROGRESS,
            dualSign = true,
            requiredRoles = listOf("QA"),
            sourceGroupIds = listOf(groupId)
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(
            groupMembershipRepository.findActiveByGroupIdAndUserId(eq(rootId), eq(groupId), eq(ownerId))
        ).thenReturn(makeMembershipDoc(groupId, ownerId))
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: force-complete via status change
        taskService.changeTaskStatus(
            taskId = taskId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = TaskStatus.DONE,
            reason = "shift ended",
            actorRoles = listOf("GROUP_MANAGER"),
            actorId = ownerId.toHexString()
        )

        // Then: history entry has via=force_complete, bypassed=true
        val updated = captured.firstValue
        assertEquals(TaskStatus.DONE.name, updated.status)
        val historyEntry = updated.history.lastOrNull()
        assertNotNull(historyEntry)
        assertEquals("TASK_STATUS_CHANGED", historyEntry!!.action)
        assertEquals("force_complete", historyEntry.payload?.get("via"))
        assertEquals(true, historyEntry.payload?.get("bypassed"))
    }

    @Test
    fun `C-008 both DONE paths emit factory-ops-task-completed event`() {
        // Path 1: qa_review_complete
        val existingReview = makeReviewDoc(ObjectId(), "SHIFT_LEAD")
        val taskDoc1 = makeTaskDoc(
            status = TaskStatus.IN_REVIEW, dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc1.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc1)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())

        taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()
        )
        verify(eventPublisher, times(1)).publishTaskCompleted(any(), eq(ownerId.toHexString()))

        // Path 2: force_complete (reset mocks)
        reset(eventPublisher, taskRepository, groupMembershipRepository)
        val groupId = ObjectId()
        val taskDoc2 = makeTaskDoc(
            status = TaskStatus.IN_PROGRESS, dualSign = true,
            requiredRoles = listOf("QA"), sourceGroupIds = listOf(groupId)
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc2)
        whenever(groupMembershipRepository.findActiveByGroupIdAndUserId(any(), eq(groupId), any()))
            .thenReturn(makeMembershipDoc(groupId, ownerId))
        doNothing().whenever(taskRepository).update(any<TaskDocument>())

        taskService.changeTaskStatus(
            taskId.toHexString(), rootId.toHexString(),
            TaskStatus.DONE, "bypass", listOf("GROUP_MANAGER"), ownerId.toHexString()
        )
        verify(eventPublisher, times(1)).publishTaskCompleted(any(), eq(ownerId.toHexString()))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-011: createTask dueAt >= project.startAt validation
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-011 should reject task creation when dueAt is before project startAt`() {
        // Given: project starts in 7 days, task due in 3 days (before project start)
        val projectStart = Instant.now().plusSeconds(7 * 86400)
        val taskDue = Instant.now().plusSeconds(3 * 86400)  // before projectStart!
        val projectDoc = makeProjectDoc(startAt = projectStart)
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)

        // When / Then
        assertThrows(ValidationException::class.java) {
            taskService.createTask(
                rootOrgId = rootId.toHexString(),
                projectId = projectId.toHexString(),
                type = "GENERAL",
                title = "Test Task",
                descriptionMarkdown = null,
                priority = Priority.NORMAL,
                ownerId = ownerId.toHexString(),
                assignees = listOf(ownerId.toHexString()),
                attributes = emptyMap(),
                schedule = TimeRange(start = null, due = taskDue),
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `C-011 should allow task creation when dueAt equals project startAt`() {
        // Given: dueAt == startAt (boundary: allowed since rule is dueAt >= startAt)
        val projectStart = Instant.now().plusSeconds(7 * 86400)
        val projectDoc = makeProjectDoc(groupIds = emptyList(), startAt = projectStart)
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        val captured = argumentCaptor<TaskDocument>()
        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<TaskDocument>(0).id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(captured.capture())
        whenever(eventPublisher.publishTaskCreated(any(), any())).then {}

        // When: task due == project start (boundary allowed)
        taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "GENERAL",
            title = "Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = ownerId.toHexString(),
            assignees = listOf(ownerId.toHexString()),
            attributes = emptyMap(),
            schedule = TimeRange(start = null, due = projectStart),  // equal to start
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: task persisted successfully
        verify(taskRepository).persist(any<TaskDocument>())
    }

    @Test
    fun `C-011 should allow task creation when dueAt is null`() {
        // Given: dueAt=null — no constraint applies
        val projectStart = Instant.now().plusSeconds(7 * 86400)
        val projectDoc = makeProjectDoc(startAt = projectStart)
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<TaskDocument>(0).id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCreated(any(), any())).then {}

        // When / Then: should not throw
        taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "GENERAL",
            title = "Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = ownerId.toHexString(),
            assignees = listOf(ownerId.toHexString()),
            attributes = emptyMap(),
            schedule = null,  // no schedule
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )
        verify(taskRepository).persist(any<TaskDocument>())
    }

    @Test
    fun `C-011 should allow task creation when project has no startAt`() {
        // Given: project has no schedule — constraint cannot fire
        val projectDoc = makeProjectDoc(startAt = null)
        whenever(projectRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(projectDoc)
        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<TaskDocument>(0).id = ObjectId()
            Unit
        }.whenever(taskRepository).persist(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCreated(any(), any())).then {}

        // When / Then: should not throw even with an early dueAt
        taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "GENERAL",
            title = "Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = ownerId.toHexString(),
            assignees = listOf(ownerId.toHexString()),
            attributes = emptyMap(),
            schedule = TimeRange(start = null, due = Instant.now()),  // any date is OK when no project start
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )
        verify(taskRepository).persist(any<TaskDocument>())
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-012: addAssignees — all must be active and in same rootOrgId
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-012 should add assignees when all users are active in same rootOrg`() {
        // Given: two active users in the same rootOrg
        val userId1 = ObjectId()
        val userId2 = ObjectId()
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId1), eq(rootId))).thenReturn(makeActiveUserDoc(userId1))
        whenever(userRepository.findByIdAndNotDeleted(eq(userId2), eq(rootId))).thenReturn(makeActiveUserDoc(userId2))
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskAssigned(any(), any())).then {}

        // When
        val result = taskService.addAssignees(
            taskId.toHexString(), rootId.toHexString(),
            listOf(userId1.toHexString(), userId2.toHexString()),
            ownerId.toHexString()
        )

        // Then
        assertNotNull(result)
        val updated = captured.firstValue
        assertTrue(updated.assignees.any { it == userId1 })
        assertTrue(updated.assignees.any { it == userId2 })
    }

    @Test
    fun `C-012 should reject addAssignees when any user is inactive`() {
        // Given: one active and one inactive user
        val activeUserId = ObjectId()
        val inactiveUserId = ObjectId()
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(activeUserId), eq(rootId))).thenReturn(makeActiveUserDoc(activeUserId))
        whenever(userRepository.findByIdAndNotDeleted(eq(inactiveUserId), eq(rootId))).thenReturn(makeInactiveUserDoc(inactiveUserId))

        // When / Then
        val ex = assertThrows(ValidationException::class.java) {
            taskService.addAssignees(
                taskId.toHexString(), rootId.toHexString(),
                listOf(activeUserId.toHexString(), inactiveUserId.toHexString()),
                ownerId.toHexString()
            )
        }
        assertTrue(
            ex.message?.contains(inactiveUserId.toHexString()) == true,
            "Exception must list the invalid user ID(s)"
        )
    }

    @Test
    fun `C-012 should reject addAssignees when user belongs to different rootOrg`() {
        // Given: user not found in rootOrg (different org = findByIdAndNotDeleted returns null)
        val foreignUserId = ObjectId()
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        // Returns null because user's rootOrgId differs — repository filters by rootOrgId
        whenever(userRepository.findByIdAndNotDeleted(eq(foreignUserId), eq(rootId))).thenReturn(null)

        // When / Then
        val ex = assertThrows(ValidationException::class.java) {
            taskService.addAssignees(
                taskId.toHexString(), rootId.toHexString(),
                listOf(foreignUserId.toHexString()),
                ownerId.toHexString()
            )
        }
        assertTrue(ex.message?.contains(foreignUserId.toHexString()) == true)
    }

    @Test
    fun `C-012 should deduplicate assignees when same userId added twice`() {
        // Given: same userId in the list twice
        val userId = ObjectId()
        val taskDoc = makeTaskDoc()
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(makeActiveUserDoc(userId))
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())
        whenever(eventPublisher.publishTaskAssigned(any(), any())).then {}

        // When
        taskService.addAssignees(
            taskId.toHexString(), rootId.toHexString(),
            listOf(userId.toHexString(), userId.toHexString()),  // duplicate
            ownerId.toHexString()
        )

        // Then: userId appears only once in assignees
        val updated = captured.firstValue
        assertEquals(1, updated.assignees.count { it == userId }, "Duplicate userId must not be added twice")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Q-23 OR: auto-complete test matrix (ADR-0011 v1.4 Amendment §2)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `Q-23 OR whitelist met with two different roles from whitelist triggers DONE`() {
        // Given: whitelist=[QA,SHIFT_LEAD], dualSignRequired=true, one existing SHIFT_LEAD review
        val existingReview = makeReviewDoc(ObjectId(), "SHIFT_LEAD")
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: submit QA review as second review
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()
        )

        // Then: auto-complete triggers (size=2 >= 2, both in whitelist)
        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `Q-23 OR same role two reviews (QA+QA two different users) triggers DONE`() {
        // Given: whitelist=[QA,SHIFT_LEAD], dualSignRequired=true, one existing QA review from different user
        val firstReviewerId = ObjectId()
        val existingReview = makeReviewDoc(firstReviewerId, "QA")
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: second reviewer submits QA (same role, different user)
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()  // ownerId != firstReviewerId
        )

        // Then: same role two different users → DONE (ADR-0011 v1.4 §3 "QA+QA, 兩位不同 user → DONE")
        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `Q-23 OR same user with two different roles (QA + SHIFT_LEAD) triggers DONE`() {
        // Given: same ownerId submits SHIFT_LEAD first (existing), now submits QA
        val existingReview = makeReviewDoc(ownerId, "SHIFT_LEAD")  // same ownerId, different role
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())
        whenever(eventPublisher.publishTaskCompleted(any(), any())).then {}

        // When: same user submits QA (different role from existing SHIFT_LEAD)
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()  // same user
        )

        // Then: Q5 B — same user multi-role signing allowed → DONE
        assertEquals(TaskStatus.DONE, result.status)
    }

    @Test
    fun `Q-23 OR same user same role duplicate (INV-36) throws ConflictException`() {
        // Given: same user already submitted QA
        val existingReview = makeReviewDoc(ownerId, "QA")  // same user, same role
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then
        assertThrows(com.factoryops.interfaces.exception.ConflictException::class.java) {
            taskService.submitReview(
                taskId.toHexString(), rootId.toHexString(),
                Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()  // same user + same role
            )
        }
    }

    @Test
    fun `Q-23 OR non-whitelist role is rejected at write time (422 role_not_required)`() {
        // Given: whitelist=[QA], user tries SHIFT_LEAD which is NOT in whitelist
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA")  // only QA in whitelist
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)

        // When / Then: SHIFT_LEAD not in whitelist → ForbiddenException at write time
        assertThrows(ForbiddenException::class.java) {
            taskService.submitReview(
                taskId.toHexString(), rootId.toHexString(),
                Role.SHIFT_LEAD, ReviewDecision.APPROVED, null, ownerId.toHexString()
            )
        }
    }

    @Test
    fun `Q-23 OR dualSignRequired=true but only 1 review stays IN_REVIEW`() {
        // Given: no existing reviews, submit one
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())

        // When
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()
        )

        // Then: stays IN_REVIEW
        assertEquals(TaskStatus.IN_REVIEW, result.status)
    }

    @Test
    fun `Q-23 OR rejection clears all qaReviews and reverts status to IN_PROGRESS`() {
        // Given: one existing APPROVED review
        val existingReview = makeReviewDoc(ObjectId(), "SHIFT_LEAD")
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = listOf(existingReview)
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        val captured = argumentCaptor<TaskDocument>()
        doNothing().whenever(taskRepository).update(captured.capture())

        // When: QA submits REJECTED
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.REJECTED, "Needs rework", ownerId.toHexString()
        )

        // Then: status = IN_PROGRESS, qaReviews cleared (Q-21)
        assertEquals(TaskStatus.IN_PROGRESS, result.status)
        val updated = captured.firstValue
        assertTrue(updated.qaReviews.isEmpty(), "qaReviews must be empty after rejection (Q-21)")
        assertTrue(updated.history.any { it.action == "TASK_REVIEW_REJECTED" }, "History must contain TASK_REVIEW_REJECTED")
    }

    @Test
    fun `Q-23 OR after rejection new approvals restart count from zero`() {
        // Given: simulate state AFTER rejection (IN_REVIEW, empty qaReviews — workflow restarted)
        val taskDoc = makeTaskDoc(
            status = TaskStatus.IN_REVIEW,
            dualSign = true,
            requiredRoles = listOf("QA", "SHIFT_LEAD")
        )
        taskDoc.qaReviews = emptyList()  // cleared by prior rejection
        whenever(taskRepository.findByIdAndRootOrg(any(), eq(rootId))).thenReturn(taskDoc)
        doNothing().whenever(taskRepository).update(any<TaskDocument>())

        // When: submit first new review
        val result = taskService.submitReview(
            taskId.toHexString(), rootId.toHexString(),
            Role.QA, ReviewDecision.APPROVED, null, ownerId.toHexString()
        )

        // Then: count=1, must stay IN_REVIEW (not auto-complete with only 1)
        assertEquals(TaskStatus.IN_REVIEW, result.status)
    }
}
