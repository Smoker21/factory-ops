package com.factoryops.unit

import com.factoryops.application.service.DispatchService
import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.TaskService
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.repository.ActionRequestRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Extended unit tests for DispatchService.
 * Covers: createActionRequest, updateActionRequest, changeActionRequestStatus,
 * deleteActionRequest, convertToTask, getActionRequest.
 */
class DispatchServiceExtendedUnitTest {

    private lateinit var actionRequestRepository: ActionRequestRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var taskService: TaskService
    private lateinit var eventPublisher: EventPublisherService
    private lateinit var dispatchService: DispatchService

    private val rootId = ObjectId()
    private val actorId = ObjectId()
    private val arId = ObjectId()
    private val orgId = ObjectId()

    @BeforeEach
    fun setup() {
        actionRequestRepository = mock()
        orgRepository = mock()
        userRepository = mock()
        taskService = mock()
        eventPublisher = mock()
        dispatchService = DispatchService(actionRequestRepository, orgRepository, userRepository, taskService, eventPublisher)
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeLeafOrgDoc(id: ObjectId = orgId, isLeaf: Boolean = true): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.type = "SECTION"
        doc.name = "Assembly Section"
        doc.code = "assembly-section"
        doc.isLeaf = isLeaf
        doc.leaderIds = listOf(ObjectId())
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeArDoc(
        id: ObjectId = arId,
        status: String = "SUBMITTED",
        linkedTaskId: ObjectId? = null,
        ownerId: ObjectId? = null
    ): ActionRequestDocument {
        val doc = ActionRequestDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.originatingOrgId = orgId
        doc.targetOrgId = orgId
        doc.title = "Test Action Request"
        doc.severity = SeverityDocument().also { s -> s.level = "HIGH"; s.reason = null }
        doc.status = status
        doc.requesterId = actorId
        doc.ownerId = ownerId
        doc.linkedTaskId = linkedTaskId
        doc.history = emptyList()
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTask(id: ObjectId = ObjectId()): Task {
        val ownerId = ObjectId().toHexString()
        return Task(
            id = id.toHexString(),
            rootOrgId = rootId.toHexString(),
            projectId = ObjectId().toHexString(),
            type = "GENERAL",
            title = "Test Task",
            status = TaskStatus.OPEN,
            priority = Priority.NORMAL,
            ownerId = ownerId,
            assignees = listOf(ownerId),
            qaReviewPolicy = QaReviewPolicy(
                dualSignRequired = false,
                requiredReviewerRoles = emptyList(),
                snapshotAt = Instant.now()
            ),
            createdAt = Instant.now(),
            createdBy = ownerId,
            updatedAt = Instant.now()
        )
    }

    // ─── getActionRequest ────────────────────────────────────────────────────────

    @Test
    fun `should throw NotFoundException when action request not found`() {
        // Given
        whenever(actionRequestRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            dispatchService.getActionRequest(arId.toHexString(), rootId.toHexString())
        }
    }

    @Test
    fun `should return ActionRequest when found`() {
        // Given
        val arDoc = makeArDoc()
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)

        // When
        val result = dispatchService.getActionRequest(arId.toHexString(), rootId.toHexString())

        // Then
        assertEquals(arId.toHexString(), result.id)
        assertEquals("Test Action Request", result.title)
    }

    // ─── createActionRequest ──────────────────────────────────────────────────────

    @Test
    fun `should reject createActionRequest when target org not found`() {
        // Given
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            dispatchService.createActionRequest(
                rootOrgId = rootId.toHexString(),
                projectId = null,
                originatingOrgId = orgId.toHexString(),
                targetOrgId = orgId.toHexString(),
                title = "Test AR",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.HIGH,
                severityReason = null,
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should reject createActionRequest when target org is not leaf`() {
        // Given
        val nonLeafOrg = makeLeafOrgDoc(isLeaf = false)
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(nonLeafOrg)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            dispatchService.createActionRequest(
                rootOrgId = rootId.toHexString(),
                projectId = null,
                originatingOrgId = orgId.toHexString(),
                targetOrgId = orgId.toHexString(),
                title = "Test AR",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should create ActionRequest successfully when target org is leaf`() {
        // Given
        val leafOrg = makeLeafOrgDoc(isLeaf = true)
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(leafOrg)
        val capturedAr = argumentCaptor<ActionRequestDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ActionRequestDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(actionRequestRepository).persist(capturedAr.capture())

        // When
        val result = dispatchService.createActionRequest(
            rootOrgId = rootId.toHexString(),
            projectId = null,
            originatingOrgId = orgId.toHexString(),
            targetOrgId = orgId.toHexString(),
            title = "Critical Machine Failure",
            descriptionMarkdown = "## Details",
            severityLevel = SeverityLevel.CRITICAL,
            severityReason = "Machine stopped production",
            actorId = actorId.toHexString()
        )

        // Then
        assertNotNull(result.id)
        assertEquals("Critical Machine Failure", result.title)
        assertEquals(SeverityLevel.CRITICAL, result.severity.level)
        assertEquals(ActionRequestStatus.SUBMITTED, result.status)
        assertEquals(1, capturedAr.firstValue.history.size)
        assertEquals("ACTION_REQUEST_CREATED", capturedAr.firstValue.history[0].action)
    }

    // ─── updateActionRequest ──────────────────────────────────────────────────────

    @Test
    fun `should update title on updateActionRequest`() {
        // Given
        val arDoc = makeArDoc()
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        val result = dispatchService.updateActionRequest(
            id = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            title = "Updated Title",
            descriptionMarkdown = "## Updated",
            actorId = actorId.toHexString()
        )

        // Then
        assertEquals("Updated Title", captured.firstValue.title)
        assertEquals("## Updated", captured.firstValue.descriptionMarkdown)
        assertTrue(captured.firstValue.history.any { it.action == "ACTION_REQUEST_UPDATED" })
    }

    @Test
    fun `should throw NotFoundException when updating non-existent AR`() {
        // Given
        whenever(actionRequestRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            dispatchService.updateActionRequest(arId.toHexString(), rootId.toHexString(), "New Title", null, actorId.toHexString())
        }
    }

    // ─── changeActionRequestStatus ────────────────────────────────────────────────

    @Test
    fun `should change status to RESOLVED and set resolvedAt`() {
        // Given
        val arDoc = makeArDoc(status = "SUBMITTED")
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        val result = dispatchService.changeActionRequestStatus(
            id = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = ActionRequestStatus.RESOLVED,
            reason = "Issue resolved",
            actorId = actorId.toHexString()
        )

        // Then
        assertEquals(ActionRequestStatus.RESOLVED, result.status)
        assertNotNull(captured.firstValue.resolvedAt, "resolvedAt must be set when status is RESOLVED")
    }

    @Test
    fun `should change status to TRIAGED without setting resolvedAt`() {
        // Given
        val arDoc = makeArDoc(status = "SUBMITTED")
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        dispatchService.changeActionRequestStatus(
            id = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = ActionRequestStatus.TRIAGED,
            reason = null,
            actorId = actorId.toHexString()
        )

        // Then
        assertNull(captured.firstValue.resolvedAt, "resolvedAt must NOT be set for non-RESOLVED status")
    }

    // ─── deleteActionRequest ──────────────────────────────────────────────────────

    @Test
    fun `should soft-delete ActionRequest by setting deletedAt`() {
        // Given
        val arDoc = makeArDoc()
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        dispatchService.deleteActionRequest(arId.toHexString(), rootId.toHexString(), actorId.toHexString())

        // Then
        verify(actionRequestRepository, never()).delete(any<ActionRequestDocument>())
        assertNotNull(captured.firstValue.deletedAt)
    }

    // ─── convertToTask ────────────────────────────────────────────────────────────

    @Test
    fun `should reject convertToTask when AR already has a linked task`() {
        // Given
        val linkedTaskId = ObjectId()
        val arDoc = makeArDoc(linkedTaskId = linkedTaskId)
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            dispatchService.convertToTask(
                actionRequestId = arId.toHexString(),
                rootOrgId = rootId.toHexString(),
                projectId = ObjectId().toHexString(),
                taskType = "GENERAL",
                taskTitle = "Converted Task",
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should convert AR to task and update AR status to TRIAGED`() {
        // Given
        val ownerObjectId = ObjectId()
        val arDoc = makeArDoc(linkedTaskId = null, ownerId = ownerObjectId)
        whenever(actionRequestRepository.findByIdAndRootOrg(eq(arId), eq(rootId))).thenReturn(arDoc)
        val task = makeTask()
        whenever(taskService.createTask(
            rootOrgId = any(),
            projectId = any(),
            type = any(),
            title = any(),
            descriptionMarkdown = org.mockito.kotlin.anyOrNull(),
            priority = any(),
            ownerId = any(),
            assignees = any(),
            attributes = any(),
            schedule = org.mockito.kotlin.anyOrNull(),
            tags = any(),
            actorId = any(),
            originActionRequestId = org.mockito.kotlin.anyOrNull()
        )).thenReturn(task)
        val captured = argumentCaptor<ActionRequestDocument>()
        doNothing().whenever(actionRequestRepository).update(captured.capture())

        // When
        val result = dispatchService.convertToTask(
            actionRequestId = arId.toHexString(),
            rootOrgId = rootId.toHexString(),
            projectId = ObjectId().toHexString(),
            taskType = "GENERAL",
            taskTitle = "Converted Task",
            actorId = actorId.toHexString()
        )

        // Then
        assertNotNull(result.id)
        assertEquals("TRIAGED", captured.firstValue.status)
        assertNotNull(captured.firstValue.linkedTaskId)
        assertTrue(captured.firstValue.history.any { it.action == "ACTION_REQUEST_TRIAGED" })
    }

    @Test
    fun `should throw NotFoundException when converting non-existent AR to task`() {
        // Given
        whenever(actionRequestRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            dispatchService.convertToTask(arId.toHexString(), rootId.toHexString(), ObjectId().toHexString(), "GENERAL", "Title", actorId.toHexString())
        }
    }
}
