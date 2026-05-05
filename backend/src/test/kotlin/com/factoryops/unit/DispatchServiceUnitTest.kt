package com.factoryops.unit

import com.factoryops.application.service.DispatchService
import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.TaskService
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.repository.ActionRequestRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for DispatchService business rules.
 * Covers: single-hop dispatch, leaf constraint, actor authorization,
 * and 0/1/N leader owner assignment rules (ADR-0008, ADR-0010, INV-24, INV-25).
 */
class DispatchServiceUnitTest {

    private lateinit var actionRequestRepository: ActionRequestRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var taskService: TaskService
    private lateinit var eventPublisher: EventPublisherService
    private lateinit var dispatchService: DispatchService

    private val rootId = ObjectId()
    private val actorId = ObjectId()

    @BeforeEach
    fun setup() {
        actionRequestRepository = mock()
        orgRepository = mock()
        userRepository = mock()
        taskService = mock()
        eventPublisher = mock()
        dispatchService = DispatchService(actionRequestRepository, orgRepository, userRepository, taskService, eventPublisher)
    }

    // ─── Helper builders ───────────────────────────────────────────────────────

    private fun makeLeafOrg(
        id: ObjectId = ObjectId(),
        leaderIds: List<ObjectId> = emptyList(),
        parentId: ObjectId? = rootId
    ): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.parentId = parentId
        doc.type = "SECTION"
        doc.name = "Test Section"
        doc.code = "sec-${id.toHexString().take(6)}"
        doc.isLeaf = true
        doc.depth = 1
        doc.ancestorIds = listOfNotNull(parentId)
        doc.leaderIds = leaderIds
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeNonLeafOrg(id: ObjectId = ObjectId()): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.type = "DEPARTMENT"
        doc.name = "Test Dept"
        doc.code = "dept-${id.toHexString().take(6)}"
        doc.isLeaf = false
        doc.depth = 1
        doc.ancestorIds = listOf(rootId)
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    // ─── INV-24: targetOrgId must be leaf ────────────────────────────────────

    @Test
    fun `should reject dispatch when target organization is not leaf`() {
        // Given
        val nonLeafOrg = makeNonLeafOrg()
        whenever(orgRepository.findByIdAndRootOrg(eq(nonLeafOrg.id!!), any())).thenReturn(nonLeafOrg)

        // When / Then
        assertThrows(ValidationException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = nonLeafOrg.id!!.toHexString(),
                title = "Test",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    // ─── INV-25: actor must be manager of an ancestor ────────────────────────

    @Test
    fun `should reject dispatch when actor is not manager of any ancestor org`() {
        // Given: leaf org with rootId as ancestor, but actor manages a completely unrelated org
        val leafOrg = makeLeafOrg(leaderIds = listOf(ObjectId()))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)

        // When / Then
        assertThrows(ForbiddenException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id!!.toHexString(),
                title = "Test",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(ObjectId().toHexString()), // unrelated org
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    @Test
    fun `should allow dispatch when actor manages the target org directly`() {
        // Given: actor manages the leaf org itself
        val leaderId = ObjectId()
        val leafOrg = makeLeafOrg(leaderIds = listOf(leaderId))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ActionRequestDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(actionRequestRepository).persist(any<ActionRequestDocument>())
        whenever(eventPublisher.publishActionRequestDispatched(any(), any())).then {}

        // When (no exception)
        val result = dispatchService.dispatchActionRequest(
            targetOrgId = leafOrg.id!!.toHexString(),
            title = "Direct dispatch",
            descriptionMarkdown = null,
            severityLevel = SeverityLevel.LOW,
            severityReason = null,
            ownerId = null,
            actorId = actorId.toHexString(),
            actorOrgManagerScopes = listOf(leafOrg.id!!.toHexString()), // manages the leaf itself
            actorRootOrgId = rootId.toHexString()
        )

        // Then
        assertNotNull(result)
    }

    // ─── ADR-0010: 0 leaders → 409 ConflictException ─────────────────────────

    @Test
    fun `should reject dispatch when target org has no leaders (0 leaders)`() {
        // Given
        val leafOrg = makeLeafOrg(leaderIds = emptyList())
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)

        // When / Then
        assertThrows(ConflictException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id!!.toHexString(),
                title = "Test",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                ownerId = null,
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    // ─── ADR-0010: 1 leader → auto-assign owner ──────────────────────────────

    @Test
    fun `should auto-assign single leader as owner when exactly 1 leader`() {
        // Given
        val leaderId = ObjectId()
        val leafOrg = makeLeafOrg(leaderIds = listOf(leaderId))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)
        val captured = argumentCaptor<ActionRequestDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ActionRequestDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(actionRequestRepository).persist(captured.capture())
        whenever(eventPublisher.publishActionRequestDispatched(any(), any())).then {}

        // When
        val result = dispatchService.dispatchActionRequest(
            targetOrgId = leafOrg.id!!.toHexString(),
            title = "Auto-assign test",
            descriptionMarkdown = null,
            severityLevel = SeverityLevel.MEDIUM,
            severityReason = null,
            ownerId = null,  // not specified
            actorId = actorId.toHexString(),
            actorOrgManagerScopes = listOf(rootId.toHexString()),
            actorRootOrgId = rootId.toHexString()
        )

        // Then: owner auto-assigned to the single leader
        assertEquals(leaderId.toHexString(), result.ownerId)
    }

    // ─── ADR-0010: N leaders → must specify ownerId ─────────────────────────

    @Test
    fun `should reject dispatch when N leaders and no ownerId specified`() {
        // Given
        val leader1 = ObjectId()
        val leader2 = ObjectId()
        val leafOrg = makeLeafOrg(leaderIds = listOf(leader1, leader2))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)

        // When / Then
        assertThrows(ValidationException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id!!.toHexString(),
                title = "N-leader test",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.HIGH,
                severityReason = null,
                ownerId = null,  // must specify
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    @Test
    fun `should succeed dispatch when N leaders and valid ownerId specified`() {
        // Given
        val leader1 = ObjectId()
        val leader2 = ObjectId()
        val leafOrg = makeLeafOrg(leaderIds = listOf(leader1, leader2))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)
        val captured = argumentCaptor<ActionRequestDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ActionRequestDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(actionRequestRepository).persist(captured.capture())
        whenever(eventPublisher.publishActionRequestDispatched(any(), any())).then {}

        // When
        val result = dispatchService.dispatchActionRequest(
            targetOrgId = leafOrg.id!!.toHexString(),
            title = "N-leader specified test",
            descriptionMarkdown = null,
            severityLevel = SeverityLevel.HIGH,
            severityReason = null,
            ownerId = leader1.toHexString(),
            actorId = actorId.toHexString(),
            actorOrgManagerScopes = listOf(rootId.toHexString()),
            actorRootOrgId = rootId.toHexString()
        )

        // Then
        assertEquals(leader1.toHexString(), result.ownerId)
    }

    @Test
    fun `should reject dispatch when specified ownerId is not in leaders list`() {
        // Given
        val leader1 = ObjectId()
        val leader2 = ObjectId()
        val randomUser = ObjectId()
        val leafOrg = makeLeafOrg(leaderIds = listOf(leader1, leader2))
        whenever(orgRepository.findByIdAndRootOrg(eq(leafOrg.id!!), any())).thenReturn(leafOrg)

        // When / Then
        assertThrows(ValidationException::class.java) {
            dispatchService.dispatchActionRequest(
                targetOrgId = leafOrg.id!!.toHexString(),
                title = "Invalid owner test",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.HIGH,
                severityReason = null,
                ownerId = randomUser.toHexString(),  // not a leader
                actorId = actorId.toHexString(),
                actorOrgManagerScopes = listOf(rootId.toHexString()),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    // ─── getActionRequest: NotFoundException ─────────────────────────────────

    @Test
    fun `should throw NotFoundException for missing action request`() {
        // Given
        whenever(actionRequestRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            dispatchService.getActionRequest(ObjectId().toHexString(), rootId.toHexString())
        }
    }

    // ─── createActionRequest: validates target is leaf ────────────────────────

    @Test
    fun `should reject createActionRequest when target org is not leaf`() {
        // Given
        val nonLeafOrg = makeNonLeafOrg()
        whenever(orgRepository.findByIdAndRootOrg(eq(nonLeafOrg.id!!), any())).thenReturn(nonLeafOrg)

        // When / Then
        assertThrows(ValidationException::class.java) {
            dispatchService.createActionRequest(
                rootOrgId = rootId.toHexString(),
                projectId = null,
                originatingOrgId = rootId.toHexString(),
                targetOrgId = nonLeafOrg.id!!.toHexString(),
                title = "Test AR",
                descriptionMarkdown = null,
                severityLevel = SeverityLevel.LOW,
                severityReason = null,
                actorId = actorId.toHexString()
            )
        }
    }
}
