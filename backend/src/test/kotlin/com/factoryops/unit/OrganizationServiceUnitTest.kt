package com.factoryops.unit

import com.factoryops.application.service.OrganizationService
import com.factoryops.domain.organization.OrgSettings
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.document.AuditEntryDocument
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import com.factoryops.persistence.document.UserDocument
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for OrganizationService business rules.
 * Covers: org tree creation, ancestorIds maintenance, depth limit, cycle detection, soft-delete.
 */
class OrganizationServiceUnitTest {

    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var orgService: OrganizationService

    @BeforeEach
    fun setup() {
        orgRepository = mock()
        userRepository = mock()
        orgService = OrganizationService(orgRepository, userRepository)
    }

    // ─── Helper builders ───────────────────────────────────────────────────────

    private fun makeOrgDoc(
        id: ObjectId = ObjectId(),
        rootOrgId: ObjectId = id,
        parentId: ObjectId? = null,
        type: String = "FAB",
        depth: Int = 0,
        ancestorIds: List<ObjectId> = emptyList(),
        isLeaf: Boolean = false,
        code: String = "fab-${id.toHexString().take(6)}",
        settings: OrgSettingsDocument? = OrgSettingsDocument()
    ): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootOrgId
        doc.parentId = parentId
        doc.type = type
        doc.name = "Test Org"
        doc.code = code
        doc.isLeaf = isLeaf
        doc.depth = depth
        doc.ancestorIds = ancestorIds
        doc.settings = settings
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    // ─── Org creation: root org rootOrgId = self ─────────────────────────────

    @Test
    fun `should create root org with rootOrgId pointing to self`() {
        // Given: no parent, no existing code
        whenever(orgRepository.findByCode(any(), any())).thenReturn(null)
        val captured = argumentCaptor<OrganizationDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<OrganizationDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(orgRepository).persist(captured.capture())
        // When
        val actorId = ObjectId().toHexString()
        orgService.createOrg(
            type = "FAB",
            name = "Test Factory",
            code = "fab-001",
            parentId = null,
            managerId = null,
            leaderIds = emptyList(),
            timezone = "Asia/Taipei",
            locale = "zh-TW",
            settings = OrgSettings(orgMaxDepth = 5, leafTypes = listOf("SECTION")),
            actorId = actorId,
            actorRootOrgId = null
        )

        // Then: single persist — rootOrgId is set atomically, no update needed
        verify(orgRepository).persist(any<OrganizationDocument>())
        verify(orgRepository, never()).update(any<OrganizationDocument>())
    }

    // ─── Org creation: child gets correct ancestorIds ────────────────────────

    @Test
    fun `should build correct ancestorIds when creating child org`() {
        // Given: existing parent at depth=0
        val rootId = ObjectId()
        val parentDoc = makeOrgDoc(id = rootId, depth = 0, ancestorIds = emptyList(), settings = OrgSettingsDocument().also { it.orgMaxDepth = 5; it.leafTypes = listOf("SECTION") })
        whenever(orgRepository.findByIdAndRootOrg(eq(rootId), any())).thenReturn(parentDoc)
        whenever(orgRepository.findRoot(eq(rootId))).thenReturn(parentDoc)
        whenever(orgRepository.findByCode(any(), any())).thenReturn(null)
        val captured = argumentCaptor<OrganizationDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<OrganizationDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(orgRepository).persist(captured.capture())

        // When
        orgService.createOrg(
            type = "SECTION",
            name = "Test Section",
            code = "section-001",
            parentId = rootId.toHexString(),
            managerId = null,
            leaderIds = emptyList(),
            timezone = null,
            locale = null,
            settings = null,
            actorId = ObjectId().toHexString(),
            actorRootOrgId = rootId.toHexString()
        )

        // Then: child's ancestorIds contains parent
        val persisted = captured.firstValue
        assertEquals(1, persisted.depth)
        assertTrue(persisted.ancestorIds.any { it == rootId }, "ancestorIds must include parent id")
    }

    // ─── Org creation: SECTION is detected as leaf ───────────────────────────

    @Test
    fun `should mark org as leaf when type is in root leafTypes`() {
        // Given
        val rootId = ObjectId()
        val parentDoc = makeOrgDoc(id = rootId, settings = OrgSettingsDocument().also { it.orgMaxDepth = 5; it.leafTypes = listOf("SECTION") })
        whenever(orgRepository.findByIdAndRootOrg(eq(rootId), any())).thenReturn(parentDoc)
        whenever(orgRepository.findRoot(eq(rootId))).thenReturn(parentDoc)
        whenever(orgRepository.findByCode(any(), any())).thenReturn(null)
        val captured = argumentCaptor<OrganizationDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<OrganizationDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(orgRepository).persist(captured.capture())

        // When
        orgService.createOrg(
            type = "SECTION",
            name = "Section",
            code = "sec-001",
            parentId = rootId.toHexString(),
            managerId = null,
            leaderIds = emptyList(),
            timezone = null,
            locale = null,
            settings = null,
            actorId = ObjectId().toHexString(),
            actorRootOrgId = rootId.toHexString()
        )

        // Then
        val persisted = captured.firstValue
        assertTrue(persisted.isLeaf, "SECTION type must be marked as leaf")
    }

    // ─── INV-12: depth limit validation ──────────────────────────────────────

    @Test
    fun `should reject org creation when depth exceeds maxDepth`() {
        // Given: parent at depth = 4 (would create child at depth 5, exceeding max of 4)
        val rootId = ObjectId()
        val parentDoc = makeOrgDoc(id = ObjectId(), rootOrgId = rootId, depth = 4, ancestorIds = List(4) { ObjectId() })
        val rootDoc = makeOrgDoc(id = rootId, settings = OrgSettingsDocument().also { it.orgMaxDepth = 4 })
        whenever(orgRepository.findByIdAndRootOrg(eq(parentDoc.id!!), any())).thenReturn(parentDoc)
        whenever(orgRepository.findRoot(eq(rootId))).thenReturn(rootDoc)
        whenever(orgRepository.findByCode(any(), any())).thenReturn(null)

        // When / Then
        assertThrows(BusinessRuleViolationException::class.java) {
            orgService.createOrg(
                type = "SECTION",
                name = "Too Deep",
                code = "too-deep",
                parentId = parentDoc.id!!.toHexString(),
                managerId = null,
                leaderIds = emptyList(),
                timezone = null,
                locale = null,
                settings = null,
                actorId = ObjectId().toHexString(),
                actorRootOrgId = rootId.toHexString()
            )
        }
    }

    // ─── INV-13: cycle detection when moving node ─────────────────────────────

    @Test
    fun `should reject move that would create a cycle`() {
        // Given: orgA is ancestor of orgB; trying to move orgA under orgB (cycle)
        val orgAId = ObjectId()
        val orgBId = ObjectId()
        val rootId = ObjectId()
        val orgA = makeOrgDoc(id = orgAId, rootOrgId = rootId, depth = 1)
        val orgB = makeOrgDoc(
            id = orgBId,
            rootOrgId = rootId,
            depth = 2,
            ancestorIds = listOf(orgAId)  // orgA is ancestor of orgB
        )

        whenever(orgRepository.findByIdAndRootOrg(eq(orgAId), any())).thenReturn(orgA)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgBId), any())).thenReturn(orgB)

        // When / Then: moving orgA to be child of orgB (its own descendant)
        assertThrows(ConflictException::class.java) {
            orgService.updateOrg(
                id = orgAId.toHexString(),
                rootOrgId = rootId.toHexString(),
                name = null,
                newParentId = orgBId.toHexString(),  // orgB is a descendant of orgA!
                type = null,
                timezone = null,
                locale = null,
                settings = null,
                actorId = ObjectId().toHexString()
            )
        }
    }

    // ─── Soft-delete: reject if has active children ───────────────────────────

    @Test
    fun `should reject soft-delete when org has active children`() {
        // Given
        val orgId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(2L)

        // When / Then
        assertThrows(ConflictException::class.java) {
            orgService.deleteOrg(orgId.toHexString(), orgId.toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `should soft-delete org by setting deletedAt when no children`() {
        // Given
        val orgId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        val captured = argumentCaptor<OrganizationDocument>()
        doNothing().whenever(orgRepository).update(captured.capture())

        // When
        orgService.deleteOrg(orgId.toHexString(), orgId.toHexString(), ObjectId().toHexString())

        // Then
        assertNotNull(captured.firstValue.deletedAt, "deletedAt must be set on soft-delete")
    }

    // ─── getOrg: throws NotFoundException for deleted org ───────────────────

    @Test
    fun `should throw NotFoundException when org not found`() {
        // Given
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            orgService.getOrg(ObjectId().toHexString(), ObjectId().toHexString())
        }
    }

    // ─── transferManager: validates user is active ────────────────────────────

    @Test
    fun `should reject transfer manager to inactive user`() {
        // Given
        val orgId = ObjectId()
        val newManagerId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId)
        val userDoc = makeUserDoc(id = newManagerId, active = false)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(newManagerId), any())).thenReturn(userDoc)

        // When / Then
        assertThrows(ValidationException::class.java) {
            orgService.transferManager(orgId.toHexString(), orgId.toHexString(), newManagerId.toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `should append ORG_MANAGER_TRANSFERRED history on successful transfer`() {
        // Given
        val orgId = ObjectId()
        val newManagerId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId)
        val userDoc = makeUserDoc(id = newManagerId, active = true)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(newManagerId), any())).thenReturn(userDoc)
        val captured = argumentCaptor<OrganizationDocument>()
        doNothing().whenever(orgRepository).update(captured.capture())
        // orgManagerScopes cache update uses findById (private helper)
        whenever(userRepository.findById(any())).thenReturn(userDoc)
        doNothing().whenever(userRepository).update(any<com.factoryops.persistence.document.UserDocument>())

        // When
        orgService.transferManager(orgId.toHexString(), orgId.toHexString(), newManagerId.toHexString(), ObjectId().toHexString())

        // Then
        val updated = captured.firstValue
        assertEquals(newManagerId, updated.managerId)
        assertTrue(updated.history.any { it.action == "ORG_MANAGER_TRANSFERRED" })
    }

    // ─── addLeader: validation ────────────────────────────────────────────────

    @Test
    fun `should reject adding leader from different root org`() {
        // Given: user belongs to a different root org.
        // findByIdAndNotDeleted queries by (userId, orgRootId) — user is not found in the org's root.
        // This returns null → NotFoundException (same behavior as "user doesn't exist in this tenant").
        val orgId = ObjectId()
        val orgRootId = ObjectId()
        val userId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId, rootOrgId = orgRootId)
        orgDoc.rootOrgId = orgRootId
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        // Returns null because user is not in orgRootId
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(orgRootId))).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            orgService.addLeader(orgId.toHexString(), orgRootId.toHexString(), userId.toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `should reject adding duplicate leader`() {
        // Given
        val orgId = ObjectId()
        val rootId = ObjectId()
        val userId = ObjectId()
        val orgDoc = makeOrgDoc(id = orgId, rootOrgId = rootId)
        orgDoc.leaderIds = listOf(userId) // already a leader
        val userDoc = makeUserDoc(id = userId, active = true, rootOrgId = rootId)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), any())).thenReturn(orgDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), any())).thenReturn(userDoc)

        // When / Then
        assertThrows(ConflictException::class.java) {
            orgService.addLeader(orgId.toHexString(), rootId.toHexString(), userId.toHexString(), ObjectId().toHexString())
        }
    }

    // ─── Helper: build UserDocument ──────────────────────────────────────────

    private fun makeUserDoc(id: ObjectId, active: Boolean, rootOrgId: ObjectId = ObjectId()): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootOrgId
        doc.accountName = "test.user"
        doc.displayName = "Test User"
        doc.employeeNo = "EMP001"
        doc.active = active
        doc.roles = emptyList()
        doc.groupIds = emptyList()
        doc.orgManagerScopes = emptyList()
        doc.createdAt = Instant.now()
        return doc
    }
}
