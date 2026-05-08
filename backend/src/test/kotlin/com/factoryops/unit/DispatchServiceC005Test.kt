package com.factoryops.unit

import com.factoryops.application.service.DispatchService
import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.TaskService
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.repository.ActionRequestRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.*
import org.mockito.kotlin.anyOrNull
import java.time.Instant

/**
 * C-005 unit tests: DispatchService.convertToTask precondition.
 *
 * Verification result: SUBMITTED is the correct precondition (verified ✓).
 * The ActionRequestStatus enum has: SUBMITTED, TRIAGED, IN_PROGRESS, RESOLVED, REJECTED.
 * State machine doc: "SUBMITTED → TRIAGED (convert-to-task)" — SUBMITTED is the only valid
 * entry state for convert-to-task. The m5-plan brief referenced "ACCEPTED" which does NOT exist
 * in the enum; builder's self-decision to use SUBMITTED is correct per ADR-0008.
 *
 * Also covers: severity-to-priority mapping (all 4 SeverityLevel values → expected Priority).
 */
class DispatchServiceC005Test {

    private lateinit var actionRequestRepository: ActionRequestRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var taskService: TaskService
    private lateinit var eventPublisher: EventPublisherService
    private lateinit var dispatchService: DispatchService

    private val rootId = ObjectId()
    private val arId = ObjectId()
    private val projectId = ObjectId()
    private val actorId = ObjectId()
    private val ownerId = ObjectId()

    @BeforeEach
    fun setup() {
        actionRequestRepository = mock()
        orgRepository = mock()
        userRepository = mock()
        taskService = mock()
        eventPublisher = mock()
        dispatchService = DispatchService(actionRequestRepository, orgRepository, userRepository, taskService, eventPublisher)
    }

    private fun makeActionRequestDoc(
        status: ActionRequestStatus,
        severity: SeverityLevel = SeverityLevel.MEDIUM,
        linkedTaskId: ObjectId? = null
    ): ActionRequestDocument {
        val doc = ActionRequestDocument()
        doc.id = arId
        doc.rootOrgId = rootId
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "Test AR"
        doc.severity = SeverityDocument().also { s ->
            s.level = severity.name
            s.reason = null
        }
        doc.status = status.name
        doc.requesterId = actorId
        doc.ownerId = ownerId
        doc.linkedTaskId = linkedTaskId
        doc.history = emptyList()
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTaskResult(priority: Priority = Priority.NORMAL): Task = Task(
        id = ObjectId().toHexString(),
        rootOrgId = rootId.toHexString(),
        projectId = projectId.toHexString(),
        type = "GENERAL",
        title = "Converted Task",
        status = TaskStatus.OPEN,
        priority = priority,
        ownerId = ownerId.toHexString(),
        assignees = listOf(ownerId.toHexString()),
        qaReviewPolicy = QaReviewPolicy(
            dualSignRequired = false,
            requiredReviewerRoles = emptyList(),
            snapshotAt = Instant.now(),
            sourceGroupIds = emptyList()
        ),
        createdBy = actorId.toHexString()
    )

    // ─── C-005: SUBMITTED precondition — success path ─────────────────────────

    @Test
    fun `should convert ActionRequest to Task when status is SUBMITTED`() {
        // Given
        val arDoc = makeActionRequestDoc(ActionRequestStatus.SUBMITTED, SeverityLevel.MEDIUM)
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        whenever(taskService.createTask(
            rootOrgId = any(), projectId = any(), type = any(), title = any(),
            descriptionMarkdown = anyOrNull(), priority = any(), ownerId = any(),
            assignees = any(), attributes = any(), schedule = anyOrNull(),
            tags = any(), actorId = any(), originActionRequestId = anyOrNull()
        )).thenReturn(makeTaskResult())
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        val result = dispatchService.convertToTask(
            actionRequestId = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            taskType = "GENERAL",
            taskTitle = "Converted Task",
            actorId = actorId.toHexString()
        )

        // Then: task is created, AR status transitions to TRIAGED
        assertNotNull(result)
        val updatedAr = captured.firstValue
        assertEquals(ActionRequestStatus.TRIAGED.name, updatedAr.status)
        assertTrue(updatedAr.history.any { it.action == "ACTION_REQUEST_TRIAGED" })
    }

    // ─── C-005: Non-SUBMITTED statuses must throw ─────────────────────────────

    @ParameterizedTest
    @EnumSource(ActionRequestStatus::class, names = ["TRIAGED", "IN_PROGRESS", "RESOLVED", "REJECTED"])
    fun `should reject convertToTask when status is not SUBMITTED`(status: ActionRequestStatus) {
        // Given: AR in a non-SUBMITTED state
        val arDoc = makeActionRequestDoc(status)
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)

        // When / Then
        val ex = assertThrows(BusinessRuleViolationException::class.java) {
            dispatchService.convertToTask(
                actionRequestId = arId.toHexString(),
                rootOrgId = rootId.toHexString(),
                projectId = projectId.toHexString(),
                taskType = "GENERAL",
                taskTitle = "Converted Task",
                actorId = actorId.toHexString()
            )
        }
        assertTrue(
            ex.message?.contains("SUBMITTED") == true,
            "Exception message must reference SUBMITTED; got: ${ex.message}"
        )
    }

    // ─── C-005: severity → priority mapping (all 4 SeverityLevel values) ──────

    @Test
    fun `should map LOW severity to LOW priority`() {
        verifyPriorityMapping(SeverityLevel.LOW, Priority.LOW)
    }

    @Test
    fun `should map MEDIUM severity to NORMAL priority`() {
        verifyPriorityMapping(SeverityLevel.MEDIUM, Priority.NORMAL)
    }

    @Test
    fun `should map HIGH severity to HIGH priority`() {
        verifyPriorityMapping(SeverityLevel.HIGH, Priority.HIGH)
    }

    @Test
    fun `should map CRITICAL severity to URGENT priority`() {
        verifyPriorityMapping(SeverityLevel.CRITICAL, Priority.URGENT)
    }

    private fun verifyPriorityMapping(severity: SeverityLevel, expectedPriority: Priority) {
        val arDoc = makeActionRequestDoc(ActionRequestStatus.SUBMITTED, severity)
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        // Capture via argumentCaptor outside the whenever call to avoid Mockito matcher mixing issues
        whenever(taskService.createTask(
            rootOrgId = any(), projectId = any(), type = any(), title = any(),
            descriptionMarkdown = anyOrNull(), priority = any(), ownerId = any(),
            assignees = any(), attributes = any(), schedule = anyOrNull(),
            tags = any(), actorId = any(), originActionRequestId = anyOrNull()
        )).thenReturn(makeTaskResult(expectedPriority))
        doNothing().whenever(actionRequestRepository).update(any<ActionRequestDocument>())

        dispatchService.convertToTask(
            actionRequestId = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            taskType = "GENERAL",
            taskTitle = "Converted Task",
            actorId = actorId.toHexString()
        )

        // Verify the correct priority was passed to taskService.createTask
        val priorityCaptor = argumentCaptor<Priority>()
        verify(taskService).createTask(
            rootOrgId = any(), projectId = any(), type = any(), title = any(),
            descriptionMarkdown = anyOrNull(),
            priority = priorityCaptor.capture(),
            ownerId = any(), assignees = any(), attributes = any(),
            schedule = anyOrNull(), tags = any(), actorId = any(),
            originActionRequestId = anyOrNull()
        )
        assertEquals(expectedPriority, priorityCaptor.firstValue,
            "SeverityLevel.$severity must map to Priority.$expectedPriority")
    }

    // ─── C-005: Already converted AR (linkedTaskId != null) ─────────────────

    @Test
    fun `should reject convertToTask when ActionRequest is already converted`() {
        // Given: already has a linkedTaskId
        val arDoc = makeActionRequestDoc(ActionRequestStatus.SUBMITTED, linkedTaskId = ObjectId())
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)

        // When / Then
        assertThrows(com.factoryops.interfaces.exception.ConflictException::class.java) {
            dispatchService.convertToTask(
                actionRequestId = arId.toHexString(),
                rootOrgId = rootId.toHexString(),
                projectId = projectId.toHexString(),
                taskType = "GENERAL",
                taskTitle = "Converted Task",
                actorId = actorId.toHexString()
            )
        }
    }
}
