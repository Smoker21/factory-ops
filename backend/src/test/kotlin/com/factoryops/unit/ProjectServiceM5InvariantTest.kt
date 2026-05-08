package com.factoryops.unit

import com.factoryops.application.service.ProjectService
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.TimeRange
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.MembershipRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
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
 * M5.4.2 invariant tests for ProjectService.
 *
 * C-009: PAUSED → COMPLETED is a valid transition (new in M5.4).
 * C-010: createProject due > start (strictly greater).
 * Full state machine parameterized scan.
 */
class ProjectServiceM5InvariantTest {

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

    private fun makeProjectDoc(status: ProjectStatus = ProjectStatus.ACTIVE): ProjectDocument {
        val doc = ProjectDocument()
        doc.id = projectId
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.name = "Test Project"
        doc.status = status.name
        doc.ownerId = ownerId
        doc.memberIds = listOf(ownerId)
        doc.groupIds = listOf(ObjectId())
        doc.history = emptyList()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeLeafOrgDoc(): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = orgId
        doc.rootOrgId = rootId
        doc.type = "SECTION"
        doc.name = "Assembly Section"
        doc.code = "assembly"
        doc.isLeaf = true
        doc.depth = 1
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeGroupDoc(id: ObjectId = ObjectId()): GroupDocument {
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

    // ═══════════════════════════════════════════════════════════════════════════
    // C-009: PAUSED → COMPLETED valid (new M5.4 transition)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-009 should allow PAUSED to COMPLETED transition`() {
        // Given
        val projectDoc = makeProjectDoc(ProjectStatus.PAUSED)
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        // When
        val result = projectService.changeProjectStatus(
            projectId.toHexString(), rootId.toHexString(),
            ProjectStatus.COMPLETED, "project completed while paused", ownerId.toHexString()
        )

        // Then
        assertEquals(ProjectStatus.COMPLETED, result.status)
    }

    @Test
    fun `C-009 should allow PAUSED to IN_PROGRESS transition`() {
        val projectDoc = makeProjectDoc(ProjectStatus.PAUSED)
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        val result = projectService.changeProjectStatus(
            projectId.toHexString(), rootId.toHexString(),
            ProjectStatus.ACTIVE, null, ownerId.toHexString()
        )

        assertEquals(ProjectStatus.ACTIVE, result.status)
    }

    @Test
    fun `C-009 should allow PAUSED to CANCELLED transition`() {
        val projectDoc = makeProjectDoc(ProjectStatus.PAUSED)
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        val result = projectService.changeProjectStatus(
            projectId.toHexString(), rootId.toHexString(),
            ProjectStatus.CANCELLED, "cancelled while paused", ownerId.toHexString()
        )

        assertEquals(ProjectStatus.CANCELLED, result.status)
    }

    @Test
    fun `C-009 should reject PAUSED to DRAFT transition`() {
        val projectDoc = makeProjectDoc(ProjectStatus.PAUSED)
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)

        assertThrows(StateTransitionException::class.java) {
            projectService.changeProjectStatus(
                projectId.toHexString(), rootId.toHexString(),
                ProjectStatus.DRAFT, null, ownerId.toHexString()
            )
        }
    }

    // ─── Full state machine parameterized scan ────────────────────────────────

    /**
     * Complete state machine transitions:
     * DRAFT: → ACTIVE ✓, → CANCELLED ✓, → PAUSED ✗, → COMPLETED ✗
     * ACTIVE: → PAUSED ✓, → COMPLETED ✓, → CANCELLED ✓, → DRAFT ✗
     * PAUSED: → ACTIVE ✓, → COMPLETED ✓, → CANCELLED ✓, → DRAFT ✗  (C-009 new)
     * COMPLETED: → (nothing) ✗
     * CANCELLED: → (nothing) ✗
     */
    @ParameterizedTest(name = "transition {0} -> {1} should be {2}")
    @CsvSource(
        // from,       to,          allowed
        "DRAFT,        ACTIVE,      true",
        "DRAFT,        CANCELLED,   true",
        "DRAFT,        PAUSED,      false",
        "DRAFT,        COMPLETED,   false",
        "ACTIVE,       PAUSED,      true",
        "ACTIVE,       COMPLETED,   true",
        "ACTIVE,       CANCELLED,   true",
        "ACTIVE,       DRAFT,       false",
        "PAUSED,       ACTIVE,      true",
        "PAUSED,       COMPLETED,   true",
        "PAUSED,       CANCELLED,   true",
        "PAUSED,       DRAFT,       false",
        "COMPLETED,    ACTIVE,      false",
        "COMPLETED,    DRAFT,       false",
        "CANCELLED,    ACTIVE,      false",
        "CANCELLED,    PAUSED,      false"
    )
    fun `state machine transition should match allowed status`(
        from: String,
        to: String,
        allowed: Boolean
    ) {
        val projectDoc = makeProjectDoc(ProjectStatus.valueOf(from))
        whenever(projectRepository.findByIdAndRootOrg(any(), any())).thenReturn(projectDoc)
        doNothing().whenever(projectRepository).update(any<ProjectDocument>())

        if (allowed) {
            val result = projectService.changeProjectStatus(
                projectId.toHexString(), rootId.toHexString(),
                ProjectStatus.valueOf(to), null, ownerId.toHexString()
            )
            assertEquals(ProjectStatus.valueOf(to), result.status)
        } else {
            assertThrows(StateTransitionException::class.java) {
                projectService.changeProjectStatus(
                    projectId.toHexString(), rootId.toHexString(),
                    ProjectStatus.valueOf(to), null, ownerId.toHexString()
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // C-010: createProject due > start (strictly greater, not equal)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    fun `C-010 should reject project creation when due equals start`() {
        // Given
        val leafOrg = makeLeafOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        val groupId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(makeGroupDoc(groupId))

        val sameInstant = Instant.now().plusSeconds(86400)

        // When / Then
        assertThrows(ValidationException::class.java) {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = emptyList(),
                groupIds = listOf(groupId.toHexString()),
                schedule = TimeRange(start = sameInstant, due = sameInstant),  // due == start!
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `C-010 should reject project creation when due is before start`() {
        // Given
        val leafOrg = makeLeafOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        val groupId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(makeGroupDoc(groupId))

        val start = Instant.now().plusSeconds(7 * 86400)
        val dueBefore = start.minusSeconds(86400)  // due BEFORE start!

        // When / Then
        assertThrows(ValidationException::class.java) {
            projectService.createProject(
                rootOrgId = rootId.toHexString(),
                organizationId = orgId.toHexString(),
                name = "Project",
                descriptionMarkdown = null,
                ownerId = ownerId.toHexString(),
                memberIds = emptyList(),
                groupIds = listOf(groupId.toHexString()),
                schedule = TimeRange(start = start, due = dueBefore),
                tags = emptyList(),
                actorId = ownerId.toHexString()
            )
        }
    }

    @Test
    fun `C-010 should allow project creation when due is strictly after start`() {
        // Given
        val leafOrg = makeLeafOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        val groupId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(makeGroupDoc(groupId))

        val start = Instant.now().plusSeconds(7 * 86400)
        val dueAfter = start.plusSeconds(86400)  // due strictly AFTER start

        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<ProjectDocument>(0).id = ObjectId()
            Unit
        }.whenever(projectRepository).persist(any<ProjectDocument>())
        doNothing().whenever(membershipRepository).persist(any<MembershipDocument>())

        // When / Then: should NOT throw
        projectService.createProject(
            rootOrgId = rootId.toHexString(),
            organizationId = orgId.toHexString(),
            name = "Valid Project",
            descriptionMarkdown = null,
            ownerId = ownerId.toHexString(),
            memberIds = emptyList(),
            groupIds = listOf(groupId.toHexString()),
            schedule = TimeRange(start = start, due = dueAfter),
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )
        verify(projectRepository).persist(any<ProjectDocument>())
    }

    @Test
    fun `C-010 should allow project creation when schedule is null`() {
        // Given: no schedule — constraint does not apply
        val leafOrg = makeLeafOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        val groupId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(makeGroupDoc(groupId))

        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<ProjectDocument>(0).id = ObjectId()
            Unit
        }.whenever(projectRepository).persist(any<ProjectDocument>())
        doNothing().whenever(membershipRepository).persist(any<MembershipDocument>())

        // When / Then
        projectService.createProject(
            rootOrgId = rootId.toHexString(),
            organizationId = orgId.toHexString(),
            name = "No Schedule Project",
            descriptionMarkdown = null,
            ownerId = ownerId.toHexString(),
            memberIds = emptyList(),
            groupIds = listOf(groupId.toHexString()),
            schedule = null,
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )
        verify(projectRepository).persist(any<ProjectDocument>())
    }

    @Test
    fun `C-010 should allow project creation when due is set but start is null`() {
        // Given: only due is set (no start constraint)
        val leafOrg = makeLeafOrgDoc()
        whenever(orgRepository.findByIdAndRootOrg(eq(orgId), eq(rootId))).thenReturn(leafOrg)
        val groupId = ObjectId()
        whenever(groupRepository.findByIdAndRootOrg(eq(groupId), eq(rootId))).thenReturn(makeGroupDoc(groupId))

        doAnswer { inv: org.mockito.invocation.InvocationOnMock ->
            inv.getArgument<ProjectDocument>(0).id = ObjectId()
            Unit
        }.whenever(projectRepository).persist(any<ProjectDocument>())
        doNothing().whenever(membershipRepository).persist(any<MembershipDocument>())

        // When / Then
        projectService.createProject(
            rootOrgId = rootId.toHexString(),
            organizationId = orgId.toHexString(),
            name = "Project with only due",
            descriptionMarkdown = null,
            ownerId = ownerId.toHexString(),
            memberIds = emptyList(),
            groupIds = listOf(groupId.toHexString()),
            schedule = TimeRange(start = null, due = Instant.now().plusSeconds(86400)),
            tags = emptyList(),
            actorId = ownerId.toHexString()
        )
        verify(projectRepository).persist(any<ProjectDocument>())
    }
}
