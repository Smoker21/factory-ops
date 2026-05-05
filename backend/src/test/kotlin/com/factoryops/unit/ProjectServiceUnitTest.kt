package com.factoryops.unit

import com.factoryops.application.service.ProjectService
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.MembershipRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
 * Pure unit tests for ProjectService business rules.
 * Covers: project creation validations, status transitions, owner change, soft-delete.
 */
class ProjectServiceUnitTest {

    private lateinit var projectRepository: ProjectRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var userRepository: UserRepository
    private lateinit var projectService: ProjectService

    private val rootId = ObjectId()
    private val orgId = ObjectId()
    private val ownerId = ObjectId()
    private val projectId = ObjectId()

    @BeforeEach
    fun setup() {
        projectRepository = mock()
        membershipRepository = mock()
        orgRepository = mock()
        groupRepository = mock()
        userRepository = mock()
        projectService = ProjectService(
            projectRepository, membershipRepository, orgRepository, groupRepository, userRepository
        )
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeLeafOrgDoc(id: ObjectId = orgId, rootOrgId: ObjectId = rootId, isLeaf: Boolean = true): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootOrgId
        doc.type = "SECTION"
        doc.name = "Assembly Section"
        doc.code = "assembly-section"
        doc.isLeaf = isLeaf
        doc.depth = 3
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeGroupDoc(id: ObjectId = ObjectId(), orgId: ObjectId = this.orgId, rootId: ObjectId = this.rootId): GroupDocument {
        val doc = GroupDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.type = "SHIFT"
        doc.name = "Night Shift"
        doc.code = "night-shift"
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeProjectDoc(
        id: ObjectId = projectId,
        rootId: ObjectId = this.rootId,
        ownerId: ObjectId = this.ownerId,
        status: String = "ACTIVE"
    ): ProjectDocument {
        val doc = ProjectDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.name = "Test Project"
        doc.status = status
        doc.ownerId = ownerId
        doc.memberIds = listOf(ownerId)
        doc.groupIds = listOf(ObjectId())
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeUserDoc(id: ObjectId = ObjectId()): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "test.user"
        doc.employeeNo = "EMP-001"
        doc.displayName = "Test User"
        doc.active = true
        doc.createdAt = Instant.now()
        return doc
    }

    // ─── createProject: org not leaf ────────────────────────────────────────────

    @Test
    fun `should reject project creation when org is not a leaf`() {
        // Given
        val nonLeafOrg = makeLeafOrgDoc(isLeaf = false)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(nonLeafOrg)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Test Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = listOf(ownerId.toHexString()),
                groupIds = listOf(ObjectId().toHexString()),
                schedule = null,
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject project creation when org not found`() {
        // Given
        whenever(orgRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Test Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = emptyList(),
                groupIds = listOf(ObjectId().toHexString()),
                schedule = null,
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject project creation when groupIds is empty`() {
        // Given
        val leafOrg = makeLeafOrgDoc(isLeaf = true)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Test Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = emptyList(),
                groupIds = emptyList(),  // empty!
                schedule = null,
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject project creation when group belongs to different org`() {
        // Given
        val leafOrg = makeLeafOrgDoc(isLeaf = true)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)

        val differentOrgId = ObjectId()
        val groupId = ObjectId()
        val groupDoc = makeGroupDoc(id = groupId, orgId = differentOrgId)  // different org!
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Test Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = emptyList(),
                groupIds = listOf(groupId.toHexString()),
                schedule = null,
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should include ownerId in memberIds on project creation`() {
        // Given
        val leafOrg = makeLeafOrgDoc(isLeaf = true)
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)

        val groupId = ObjectId()
        val groupDoc = makeGroupDoc(id = groupId, orgId = orgId)
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(groupDoc)

        val capturedProject = argumentCaptor<ProjectDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ProjectDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(projectRepository).persist(capturedProject.capture())
        doNothing().whenever(membershipRepository).persist(any<MembershipDocument>())

        // When
        projectService.createProject(
            rootOrgId = rootId.toHexString(),
            organizationId = orgId.toHexString(),
            name = "Test Project",
            descriptionMarkdown = null,
            ownerId = ownerId.toHexString(),
            memberIds = emptyList(),  // no explicit members
            groupIds = listOf(groupId.toHexString()),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )

        // Then: ownerId must be in memberIds
        val persisted = capturedProject.firstValue
        assertTrue(persisted.memberIds.contains(ownerId), "ownerId must be in memberIds")
    }

    // ─── getProject: throws NotFoundException for missing ──────────────────────

    @Test
    fun `should throw NotFoundException when project not found`() {
        // Given
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            projectService.getProject(ObjectId().toHexString(), rootId.toHexString())
        }
    }

    // ─── changeProjectStatus: DRAFT → ACTIVE allowed ──────────────────────────

    @Test
    fun `should allow DRAFT to ACTIVE status transition`() {
        // Given
        val projectDoc = makeProjectDoc(status = "DRAFT")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        // When
        val result = projectService.changeProjectStatus(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = ProjectStatus.ACTIVE,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(ProjectStatus.ACTIVE, result.status)
    }

    @Test
    fun `should allow ACTIVE to PAUSED status transition`() {
        // Given
        val projectDoc = makeProjectDoc(status = "ACTIVE")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        // When
        val result = projectService.changeProjectStatus(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = ProjectStatus.PAUSED,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(ProjectStatus.PAUSED, result.status)
    }

    @Test
    fun `should allow ACTIVE to COMPLETED status transition`() {
        // Given
        val projectDoc = makeProjectDoc(status = "ACTIVE")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        // When
        val result = projectService.changeProjectStatus(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newStatus = ProjectStatus.COMPLETED,
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then
        assertEquals(ProjectStatus.COMPLETED, result.status)
    }

    @Test
    fun `should reject COMPLETED to ACTIVE transition as terminal state`() {
        // Given
        val projectDoc = makeProjectDoc(status = "COMPLETED")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<StateTransitionException> {
            projectService.changeProjectStatus(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = ProjectStatus.ACTIVE,
                reason = null,
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject CANCELLED to PAUSED transition as terminal state`() {
        // Given
        val projectDoc = makeProjectDoc(status = "CANCELLED")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<StateTransitionException> {
            projectService.changeProjectStatus(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = ProjectStatus.PAUSED,
                reason = null,
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject DRAFT to COMPLETED transition as invalid`() {
        // Given
        val projectDoc = makeProjectDoc(status = "DRAFT")
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<StateTransitionException> {
            projectService.changeProjectStatus(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                newStatus = ProjectStatus.COMPLETED,
                reason = null,
                actorId = ownerId.toHexString()
            )
        }
    }

    // ─── changeProjectOwner ──────────────────────────────────────────────────────

    @Test
    fun `should change project owner and update ownerId field`() {
        // Given
        val newOwnerId = ObjectId()
        val projectDoc = makeProjectDoc(ownerId = ownerId)
        projectDoc.memberIds = listOf(ownerId, newOwnerId)  // new owner already a member
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(membershipRepository).persist(any<MembershipDocument>())
        val captured = argumentCaptor<ProjectDocument>()
        doNothing().whenever(projectRepository).update(captured.capture())

        // When
        projectService.changeProjectOwner(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newOwnerId = newOwnerId.toHexString(),
            reason = "Shift change",
            actorId = ownerId.toHexString()
        )

        // Then
        val updated = captured.firstValue
        assertEquals(newOwnerId, updated.ownerId)
    }

    @Test
    fun `should add new owner as member when not already in memberIds`() {
        // Given
        val newOwnerId = ObjectId()
        val projectDoc = makeProjectDoc(ownerId = ownerId)
        projectDoc.memberIds = listOf(ownerId)  // new owner NOT a member
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        val capturedMembership = argumentCaptor<MembershipDocument>()
        doNothing().whenever(membershipRepository).persist(capturedMembership.capture())
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        // When
        projectService.changeProjectOwner(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            newOwnerId = newOwnerId.toHexString(),
            reason = null,
            actorId = ownerId.toHexString()
        )

        // Then
        val persistedMembership = capturedMembership.firstValue
        assertEquals(newOwnerId, persistedMembership.userId)
        assertEquals(MembershipRole.LEAD.name, persistedMembership.role)
    }

    // ─── deleteProject: soft-delete ─────────────────────────────────────────────

    @Test
    fun `should soft-delete project by setting deletedAt`() {
        // Given
        val projectDoc = makeProjectDoc()
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        val captured = argumentCaptor<ProjectDocument>()
        doNothing().whenever(projectRepository).update(captured.capture())

        // When
        projectService.deleteProject(projectId.toHexString(), rootId.toHexString(), ownerId.toHexString())

        // Then: update called (not delete), deletedAt set
        verify(projectRepository, never()).delete(any<ProjectDocument>())
        assertNotNull(captured.firstValue.deletedAt, "deletedAt must be set on soft-delete")
    }

    @Test
    fun `should throw NotFoundException when deleting non-existent project`() {
        // Given
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            projectService.deleteProject(projectId.toHexString(), rootId.toHexString(), ownerId.toHexString())
        }
    }

    // ─── addMember: conflict when already member ─────────────────────────────────

    @Test
    fun `should reject adding user who is already a project member`() {
        // Given
        val userId = ObjectId()
        val projectDoc = makeProjectDoc()
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(makeUserDoc(id = userId))
        whenever(membershipRepository.findByProjectIdAndUserId(any(), any(), eq(userId)))
            .thenReturn(MembershipDocument())  // already exists!

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            projectService.addMember(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                userId = userId.toHexString(),
                role = MembershipRole.MEMBER,
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `should reject adding user not found in the org`() {
        // Given
        val userId = ObjectId()
        val projectDoc = makeProjectDoc()
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(null)  // user not found

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            projectService.addMember(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                userId = userId.toHexString(),
                role = MembershipRole.MEMBER,
                actorId = ownerId.toHexString()
            )
        }
    }

    // ─── removeMember: cannot remove owner ─────────────────────────────────────

    @Test
    fun `should reject removing project owner from membership`() {
        // Given
        val projectDoc = makeProjectDoc(ownerId = ownerId)
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            projectService.removeMember(
                projectId = projectId.toHexString(),
                rootOrgId = rootId.toHexString(),
                userId = ownerId.toHexString(),  // trying to remove the owner
                actorId = ownerId.toHexString()
            )
        }
    }

    // ─── updateProject ───────────────────────────────────────────────────────────

    @Test
    fun `should update project name and append history entry`() {
        // Given
        val projectDoc = makeProjectDoc()
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        val captured = argumentCaptor<ProjectDocument>()
        doNothing().whenever(projectRepository).update(captured.capture())

        // When
        projectService.updateProject(
            projectId = projectId.toHexString(),
            rootOrgId = rootId.toHexString(),
            name = "Updated Project Name",
            descriptionMarkdown = null,
            schedule = null,
            tags = null,
            actorId = ownerId.toHexString()
        )

        // Then
        val updated = captured.firstValue
        assertEquals("Updated Project Name", updated.name)
        assertTrue(updated.history.any { it.action == "PROJECT_UPDATED" })
    }
}
