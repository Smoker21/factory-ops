package com.factoryops.unit

import com.factoryops.application.service.OrganizationService
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

/**
 * C-014 unit tests: OrganizationService.deleteOrg must block deletion when
 * active projects or groups exist under the org.
 */
class OrganizationServiceC014Test {

    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var orgService: OrganizationService

    private val rootId = ObjectId()
    private val orgId = ObjectId()

    @BeforeEach
    fun setup() {
        orgRepository = mock()
        userRepository = mock()
        projectRepository = mock()
        groupRepository = mock()
        orgService = OrganizationService(orgRepository, userRepository, projectRepository, groupRepository)
    }

    private fun makeOrgDoc(id: ObjectId = orgId): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.type = "SECTION"
        doc.name = "Test Section"
        doc.code = "test-sec"
        doc.isLeaf = true
        doc.depth = 1
        doc.ancestorIds = emptyList()
        doc.settings = null
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    // ─── C-014: delete blocked when active projects exist ───────────────────

    @Test
    fun `C-014 should reject deleteOrg when org has active projects`() {
        // Given
        val orgDoc = makeOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        whenever(projectRepository.countActiveByOrg(eq(orgId))).thenReturn(2L)  // 2 active projects!
        whenever(groupRepository.countByOrg(eq(orgId))).thenReturn(0L)

        // When / Then
        val ex = assertThrows(ConflictException::class.java) {
            orgService.deleteOrg(orgId.toHexString(), rootId.toHexString(), ObjectId().toHexString())
        }
        assertTrue(ex.message?.contains("active project") == true || ex.message?.contains("active resource") == true)
    }

    @Test
    fun `C-014 should reject deleteOrg when org has active groups`() {
        // Given
        val orgDoc = makeOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        whenever(projectRepository.countActiveByOrg(eq(orgId))).thenReturn(0L)
        whenever(groupRepository.countByOrg(eq(orgId))).thenReturn(3L)  // 3 groups!

        // When / Then
        val ex = assertThrows(ConflictException::class.java) {
            orgService.deleteOrg(orgId.toHexString(), rootId.toHexString(), ObjectId().toHexString())
        }
        assertNotNull(ex.message)
    }

    @Test
    fun `C-014 should reject deleteOrg when org has both active projects and groups`() {
        // Given: both active projects and groups
        val orgDoc = makeOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        whenever(projectRepository.countActiveByOrg(eq(orgId))).thenReturn(1L)
        whenever(groupRepository.countByOrg(eq(orgId))).thenReturn(1L)

        // When / Then
        assertThrows(ConflictException::class.java) {
            orgService.deleteOrg(orgId.toHexString(), rootId.toHexString(), ObjectId().toHexString())
        }
    }

    // ─── C-014: delete succeeds when no active resources ──────────────────────

    @Test
    fun `C-014 should soft-delete org when it has no active projects or groups`() {
        // Given
        val orgDoc = makeOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        whenever(projectRepository.countActiveByOrg(eq(orgId))).thenReturn(0L)
        whenever(groupRepository.countByOrg(eq(orgId))).thenReturn(0L)
        val captured = argumentCaptor<OrganizationDocument>()
        doNothing().whenever(orgRepository).update(captured.capture())

        // When
        orgService.deleteOrg(orgId.toHexString(), rootId.toHexString(), ObjectId().toHexString())

        // Then: soft-delete (deletedAt set)
        assertNotNull(captured.firstValue.deletedAt, "deletedAt must be set on soft-delete")
    }

    @Test
    fun `C-014 error message includes active project and group counts`() {
        // Given: both non-zero
        val orgDoc = makeOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(orgDoc)
        whenever(orgRepository.countChildren(eq(orgId))).thenReturn(0L)
        whenever(projectRepository.countActiveByOrg(eq(orgId))).thenReturn(2L)
        whenever(groupRepository.countByOrg(eq(orgId))).thenReturn(3L)

        // When / Then: message contains counts
        val ex = assertThrows(ConflictException::class.java) {
            orgService.deleteOrg(orgId.toHexString(), rootId.toHexString(), ObjectId().toHexString())
        }
        assertNotNull(ex.message)
        // Message mentions both 2 and 3
        assertTrue(
            ex.message!!.contains("2") && ex.message!!.contains("3"),
            "Error message should contain project count (2) and group count (3); got: ${ex.message}"
        )
    }
}
