package com.factoryops

import com.factoryops.application.service.TaskService
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.document.QaReviewPolicyDocument
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
class TaskServiceTest {

    @Inject
    lateinit var taskService: TaskService

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var projectRepository: ProjectRepository

    @Inject
    lateinit var groupRepository: GroupRepository

    @Inject
    lateinit var orgRepository: OrganizationRepository

    private fun createTestSetup(): Triple<ObjectId, ObjectId, ObjectId> {
        val rootId = ObjectId()
        val sectionId = ObjectId()
        val groupId = ObjectId()
        val userId = ObjectId()
        val now = Instant.now()

        // Create section org
        val section = OrganizationDocument().also { d ->
            d.id = sectionId
            d.rootOrgId = rootId
            d.type = "SECTION"
            d.name = "Test Section"
            d.code = "test-section-${System.currentTimeMillis()}"
            d.isLeaf = true
            d.depth = 1
            d.ancestorIds = listOf(rootId)
            d.createdAt = now
            d.updatedAt = now
        }
        orgRepository.persist(section)

        // Create group with QA settings
        val group = GroupDocument().also { d ->
            d.id = groupId
            d.rootOrgId = rootId
            d.organizationId = sectionId
            d.type = "SHIFT"
            d.name = "Test Group"
            d.code = "test-group-${System.currentTimeMillis()}"
            d.settings = GroupSettingsDocument().also { s ->
                s.qa = QaSettingsDocument().also { q ->
                    q.dualSignRequired = false
                    q.requiredReviewerRoles = emptyList()
                }
            }
            d.createdAt = now
            d.updatedAt = now
        }
        groupRepository.persist(group)

        // Create project
        val project = ProjectDocument().also { d ->
            d.rootOrgId = rootId
            d.organizationId = sectionId
            d.name = "Test Project"
            d.status = "ACTIVE"
            d.ownerId = userId
            d.memberIds = listOf(userId)
            d.groupIds = listOf(groupId)
            d.createdAt = now
            d.updatedAt = now
        }
        projectRepository.persist(project)

        return Triple(rootId, project.id, userId)
    }

    @Test
    fun `createTask creates task successfully with OPEN status`() {
        val (rootId, projectId, userId) = createTestSetup()

        val task = taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "EQUIPMENT_INSPECTION",
            title = "Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = userId.toHexString(),
            assignees = listOf(userId.toHexString()),
            attributes = mapOf("equipmentId" to "EQ-001"),
            schedule = null,
            tags = listOf("test"),
            actorId = userId.toHexString()
        )

        assertNotNull(task.id)
        assertEquals("EQUIPMENT_INSPECTION", task.type)
        assertEquals("Test Task", task.title)
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(userId.toHexString(), task.ownerId)
        assertTrue(task.assignees.contains(userId.toHexString()))
    }

    @Test
    fun `changeTaskStatus transitions OPEN to IN_PROGRESS successfully`() {
        val (rootId, projectId, userId) = createTestSetup()
        val task = taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "SHIFT_HANDOVER",
            title = "Handover Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = userId.toHexString(),
            assignees = emptyList(),
            attributes = mapOf("outgoingShiftId" to "s1", "incomingShiftId" to "s2"),
            schedule = null,
            tags = emptyList(),
            actorId = userId.toHexString()
        )

        val updated = taskService.changeTaskStatus(
            taskId = task.id!!,
            rootOrgId = rootId.toHexString(),
            newStatus = TaskStatus.IN_PROGRESS,
            reason = null,
            actorRoles = emptyList(),
            actorId = userId.toHexString()
        )

        assertEquals(TaskStatus.IN_PROGRESS, updated.status)
    }

    @Test
    fun `changeTaskStatus rejects invalid transition OPEN to DONE`() {
        val (rootId, projectId, userId) = createTestSetup()
        val task = taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "SHIFT_HANDOVER",
            title = "Test Task 2",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = userId.toHexString(),
            assignees = emptyList(),
            attributes = mapOf("outgoingShiftId" to "s1", "incomingShiftId" to "s2"),
            schedule = null,
            tags = emptyList(),
            actorId = userId.toHexString()
        )

        assertThrows(StateTransitionException::class.java) {
            taskService.changeTaskStatus(
                taskId = task.id!!,
                rootOrgId = rootId.toHexString(),
                newStatus = TaskStatus.DONE,
                reason = null,
                actorRoles = emptyList(),
                actorId = userId.toHexString()
            )
        }
    }

    @Test
    fun `getTask throws NotFoundException for non-existent task`() {
        assertThrows(NotFoundException::class.java) {
            taskService.getTask(ObjectId().toHexString(), ObjectId().toHexString())
        }
    }

    @Test
    fun `removeAssignee fails when removing owner`() {
        val (rootId, projectId, userId) = createTestSetup()
        val task = taskService.createTask(
            rootOrgId = rootId.toHexString(),
            projectId = projectId.toHexString(),
            type = "SHIFT_HANDOVER",
            title = "Owner Test Task",
            descriptionMarkdown = null,
            priority = Priority.NORMAL,
            ownerId = userId.toHexString(),
            assignees = emptyList(),
            attributes = mapOf("outgoingShiftId" to "s1", "incomingShiftId" to "s2"),
            schedule = null,
            tags = emptyList(),
            actorId = userId.toHexString()
        )

        assertThrows(ConflictException::class.java) {
            taskService.removeAssignee(task.id!!, rootId.toHexString(), userId.toHexString(), userId.toHexString())
        }
    }
}
