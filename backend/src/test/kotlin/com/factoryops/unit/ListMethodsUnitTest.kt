package com.factoryops.unit

import com.factoryops.application.service.DispatchService
import com.factoryops.application.service.EventPublisherService
import com.factoryops.application.service.GroupService
import com.factoryops.application.service.OrganizationService
import com.factoryops.application.service.ProjectService
import com.factoryops.application.service.TaskService
import com.factoryops.application.service.UserService
import com.factoryops.application.auth.PasswordHasher
import com.factoryops.infrastructure.hr.HrClient
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.ActionRequestRepository
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.MembershipRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Unit tests for list/paginated methods in various services.
 * Covers: listOrgs, listGroups, listProjects, listActionRequests, listTasks, listUsers.
 */
class ListMethodsUnitTest {

    // Repositories
    private lateinit var orgRepository: OrganizationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var groupRepository: GroupRepository
    private lateinit var groupMembershipRepository: GroupMembershipRepository
    private lateinit var projectRepository: ProjectRepository
    private lateinit var membershipRepository: MembershipRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var actionRequestRepository: ActionRequestRepository
    private lateinit var credentialsRepository: UserCredentialsRepository
    private lateinit var hrClient: HrClient
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var eventPublisher: EventPublisherService

    // Services under test
    private lateinit var orgService: OrganizationService
    private lateinit var groupService: GroupService
    private lateinit var projectService: ProjectService
    private lateinit var taskService: TaskService
    private lateinit var dispatchService: DispatchService
    private lateinit var userService: UserService

    private val rootId = ObjectId()

    @BeforeEach
    fun setup() {
        orgRepository = mock()
        userRepository = mock()
        groupRepository = mock()
        groupMembershipRepository = mock()
        projectRepository = mock()
        membershipRepository = mock()
        taskRepository = mock()
        actionRequestRepository = mock()
        credentialsRepository = mock()
        hrClient = mock()
        passwordHasher = mock()
        eventPublisher = mock()

        orgService = OrganizationService(orgRepository, userRepository)
        groupService = GroupService(groupRepository, groupMembershipRepository, orgRepository, userRepository)
        projectService = ProjectService(projectRepository, membershipRepository, orgRepository, groupRepository, userRepository)
        taskService = TaskService(taskRepository, projectRepository, groupRepository, userRepository, eventPublisher)
        dispatchService = DispatchService(actionRequestRepository, orgRepository, userRepository,
            TaskService(taskRepository, projectRepository, groupRepository, userRepository, eventPublisher), eventPublisher)
        userService = UserService(userRepository, credentialsRepository, hrClient, passwordHasher)
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeOrgDoc(id: ObjectId = ObjectId()): OrganizationDocument {
        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.type = "SECTION"
        doc.name = "Section ${id.toHexString().take(4)}"
        doc.code = "section-${id.toHexString().take(4)}"
        doc.isLeaf = true
        doc.depth = 3
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeGroupDoc(id: ObjectId = ObjectId()): GroupDocument {
        val doc = GroupDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.type = "SHIFT"
        doc.name = "Group ${id.toHexString().take(4)}"
        doc.code = "grp-${id.toHexString().take(4)}"
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument()
        }
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeProjectDoc(id: ObjectId = ObjectId()): ProjectDocument {
        val doc = ProjectDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = ObjectId()
        doc.name = "Project ${id.toHexString().take(4)}"
        doc.status = "ACTIVE"
        doc.ownerId = ObjectId()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTaskDoc(id: ObjectId = ObjectId()): TaskDocument {
        val doc = TaskDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.projectId = ObjectId()
        doc.type = "GENERAL"
        doc.title = "Task ${id.toHexString().take(4)}"
        doc.status = "OPEN"
        doc.priority = "NORMAL"
        doc.ownerId = ObjectId()
        doc.assignees = listOf(doc.ownerId)
        doc.qaReviewPolicy = QaReviewPolicyDocument()
        doc.createdAt = Instant.now()
        doc.createdBy = doc.ownerId
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeArDoc(id: ObjectId = ObjectId()): ActionRequestDocument {
        val doc = ActionRequestDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.originatingOrgId = ObjectId()
        doc.targetOrgId = ObjectId()
        doc.title = "AR ${id.toHexString().take(4)}"
        doc.severity = SeverityDocument().also { s -> s.level = "LOW" }
        doc.status = "SUBMITTED"
        doc.requesterId = ObjectId()
        doc.submittedAt = Instant.now()
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeUserDoc(id: ObjectId = ObjectId()): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "user-${id.toHexString().take(4)}"
        doc.employeeNo = "EMP-${id.toHexString().take(4)}"
        doc.displayName = "User ${id.toHexString().take(4)}"
        doc.active = true
        doc.createdAt = Instant.now()
        return doc
    }

    // ─── OrganizationService.listOrgs ────────────────────────────────────────────

    @Test
    fun `listOrgs with no filter returns paginated results`() {
        // Given
        val orgs = listOf(makeOrgDoc(), makeOrgDoc(), makeOrgDoc())
        whenever(orgRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(orgs)

        // When
        val (items, pageInfo) = orgService.listOrgs(rootId.toHexString(), parentId = null, type = null, leafOnly = false, underOrgId = null, cursor = null, limit = 10)

        // Then
        assertEquals(3, items.size)
        assertFalse(pageInfo.hasMore)
        assertNull(pageInfo.nextCursor)
    }

    @Test
    fun `listOrgs hasMore is true when results exceed pageSize`() {
        // Given
        val orgs = (1..6).map { makeOrgDoc() }  // 6 results for limit=5 (pageSize+1)
        whenever(orgRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(orgs)

        // When
        val (items, pageInfo) = orgService.listOrgs(rootId.toHexString(), parentId = null, type = null, leafOnly = false, underOrgId = null, cursor = null, limit = 5)

        // Then
        assertEquals(5, items.size)
        assertTrue(pageInfo.hasMore)
        // nextCursor should be the id of the last item
        assertEquals(items.last().id, pageInfo.nextCursor)
    }

    // ─── GroupService.listGroups ──────────────────────────────────────────────────

    @Test
    fun `listGroups with no filter returns paginated results`() {
        // Given
        val groups = listOf(makeGroupDoc(), makeGroupDoc())
        whenever(groupRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(groups)

        // When
        val (items, pageInfo) = groupService.listGroups(rootId.toHexString(), organizationId = null, type = null, underOrgId = null, limit = 10)

        // Then
        assertEquals(2, items.size)
        assertFalse(pageInfo.hasMore)
    }

    @Test
    fun `listGroups by organizationId filters correctly`() {
        // Given
        val orgOId = ObjectId()
        val groups = listOf(makeGroupDoc())
        whenever(groupRepository.findByOrganizationId(any(), any())).thenReturn(groups)

        // When
        val (items, pageInfo) = groupService.listGroups(rootId.toHexString(), organizationId = orgOId.toHexString(), type = null, underOrgId = null, limit = 10)

        // Then
        assertEquals(1, items.size)
        assertFalse(pageInfo.hasMore)
    }

    // ─── ProjectService.listProjects ──────────────────────────────────────────────

    @Test
    fun `listProjects with no filter returns cursor paginated results`() {
        // Given
        val projects = listOf(makeProjectDoc(), makeProjectDoc())
        whenever(projectRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(projects)

        // When
        val (items, pageInfo) = projectService.listProjects(rootId.toHexString(), status = null, ownerId = null, memberId = null, groupId = null, limit = 10)

        // Then
        assertEquals(2, items.size)
        assertFalse(pageInfo.hasMore)
    }

    @Test
    fun `listProjects by status filters correctly`() {
        // Given
        val projects = listOf(makeProjectDoc())
        whenever(projectRepository.findByStatus(any(), any())).thenReturn(projects)

        // When
        val (items, pageInfo) = projectService.listProjects(rootId.toHexString(), status = "ACTIVE", ownerId = null, memberId = null, groupId = null, limit = 10)

        // Then
        assertEquals(1, items.size)
    }

    @Test
    fun `listProjects by ownerId filters correctly`() {
        // Given
        val ownerId = ObjectId()
        val projects = listOf(makeProjectDoc())
        whenever(projectRepository.findByOwnerId(any(), any())).thenReturn(projects)

        // When
        val (items, _) = projectService.listProjects(rootId.toHexString(), status = null, ownerId = ownerId.toHexString(), memberId = null, groupId = null, limit = 10)

        // Then
        assertEquals(1, items.size)
    }

    // ─── TaskService.listTasks ────────────────────────────────────────────────────

    @Test
    fun `listTasks with projectId filter returns results`() {
        // Given
        val projectId = ObjectId()
        val tasks = listOf(makeTaskDoc(), makeTaskDoc())
        whenever(taskRepository.findByProjectId(any(), any())).thenReturn(tasks)

        // When
        val (items, pageInfo) = taskService.listTasks(rootId.toHexString(), projectId = projectId.toHexString(), status = null, ownerId = null, assigneeId = null, type = null, limit = 10)

        // Then
        assertEquals(2, items.size)
        assertFalse(pageInfo.hasMore)
    }

    @Test
    fun `listTasks with no filter returns cursor paginated results`() {
        // Given
        val tasks = listOf(makeTaskDoc())
        whenever(taskRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(tasks)

        // When
        val (items, _) = taskService.listTasks(rootId.toHexString(), projectId = null, status = null, ownerId = null, assigneeId = null, type = null, limit = 10)

        // Then
        assertEquals(1, items.size)
    }

    // ─── DispatchService.listActionRequests ──────────────────────────────────────

    @Test
    fun `listActionRequests with no filter returns cursor paginated results`() {
        // Given
        val ars = listOf(makeArDoc(), makeArDoc())
        whenever(actionRequestRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(ars)

        // When
        val (items, pageInfo) = dispatchService.listActionRequests(rootId.toHexString(), status = null, requesterId = null, limit = 10)

        // Then
        assertEquals(2, items.size)
        assertFalse(pageInfo.hasMore)
    }

    @Test
    fun `listActionRequests by status filters correctly`() {
        // Given
        val ars = listOf(makeArDoc())
        whenever(actionRequestRepository.findByStatus(any(), any())).thenReturn(ars)

        // When
        val (items, _) = dispatchService.listActionRequests(rootId.toHexString(), status = "SUBMITTED", requesterId = null, limit = 10)

        // Then
        assertEquals(1, items.size)
    }

    // ─── UserService.listUsers ────────────────────────────────────────────────────

    @Test
    fun `listUsers with active filter returns active users`() {
        // Given
        val users = listOf(makeUserDoc(), makeUserDoc())
        whenever(userRepository.findActiveByRootOrgId(any())).thenReturn(users)

        // When
        val (items, pageInfo) = userService.listUsers(rootId.toHexString(), q = null, active = true, limit = 10)

        // Then
        assertEquals(2, items.size)
        assertFalse(pageInfo.hasMore)
    }

    @Test
    fun `listUsers with keyword search returns matching users`() {
        // Given
        val users = listOf(makeUserDoc())
        whenever(userRepository.searchByKeyword(any(), any())).thenReturn(users)

        // When
        val (items, _) = userService.listUsers(rootId.toHexString(), q = "chen", active = null, limit = 10)

        // Then
        assertEquals(1, items.size)
    }

    @Test
    fun `listUsers with no filter uses cursor pagination`() {
        // Given
        val users = listOf(makeUserDoc(), makeUserDoc())
        whenever(userRepository.findWithCursor(any(), org.mockito.kotlin.anyOrNull(), any())).thenReturn(users)

        // When
        val (items, pageInfo) = userService.listUsers(rootId.toHexString(), q = null, active = null, limit = 10)

        // Then
        assertEquals(2, items.size)
    }
}
