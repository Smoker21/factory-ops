package com.factoryops.unit

import com.factoryops.application.service.TemplateService
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.ProjectTemplateDocument
import com.factoryops.persistence.document.TaskTemplateDocument
import com.factoryops.persistence.repository.ProjectTemplateRepository
import com.factoryops.persistence.repository.TaskTemplateRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Pure unit tests for TemplateService business rules.
 * Covers: GLOBAL/ORG scope distinction, fork-from-global, deactivate (active=false),
 * code uniqueness on fork, and NotFoundException for missing templates.
 * (INV-17, INV-18, INV-27, INV-28)
 */
class TemplateServiceUnitTest {

    private lateinit var projectTemplateRepository: ProjectTemplateRepository
    private lateinit var taskTemplateRepository: TaskTemplateRepository
    private lateinit var templateService: TemplateService

    private val rootId = ObjectId()
    private val actorId = ObjectId()

    @BeforeEach
    fun setup() {
        projectTemplateRepository = mock()
        taskTemplateRepository = mock()
        templateService = TemplateService(projectTemplateRepository, taskTemplateRepository)
    }

    // ─── Helper builders ───────────────────────────────────────────────────────

    private fun makeProjectTemplateDoc(
        id: ObjectId = ObjectId(),
        scope: String = "GLOBAL",
        code: String = "tpl-${id.toHexString().take(6)}",
        version: Int = 1,
        active: Boolean = true,
        rootOrgId: ObjectId? = null
    ): ProjectTemplateDocument {
        val doc = ProjectTemplateDocument()
        doc.id = id
        doc.scope = scope
        doc.rootOrgId = rootOrgId
        doc.name = "Test Template"
        doc.code = code
        doc.active = active
        doc.version = version
        doc.createdBy = actorId
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    private fun makeTaskTemplateDoc(
        id: ObjectId = ObjectId(),
        scope: String = "GLOBAL",
        code: String = "task-tpl-${id.toHexString().take(6)}",
        active: Boolean = true
    ): TaskTemplateDocument {
        val doc = TaskTemplateDocument()
        doc.id = id
        doc.scope = scope
        doc.name = "Task Template"
        doc.code = code
        doc.type = "EQUIPMENT_INSPECTION"
        doc.defaultPriority = "NORMAL"
        doc.active = active
        doc.version = 1
        doc.createdBy = actorId
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()
        return doc
    }

    // ─── INV-17: fork must be from GLOBAL scope ───────────────────────────────

    @Test
    fun `should reject fork when source template is not GLOBAL scope`() {
        // Given
        val orgTemplate = makeProjectTemplateDoc(scope = "ORG", rootOrgId = rootId)
        whenever(projectTemplateRepository.findByIdNotDeleted(eq(orgTemplate.id!!))).thenReturn(orgTemplate)

        // When / Then
        assertThrows(ValidationException::class.java) {
            templateService.forkProjectTemplate(
                globalId = orgTemplate.id!!.toHexString(),
                rootOrgId = rootId.toHexString(),
                overrideCode = null,
                overrideName = null,
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should fork global template into ORG scope successfully`() {
        // Given
        val globalTemplate = makeProjectTemplateDoc(scope = "GLOBAL", code = "global-tpl")
        whenever(projectTemplateRepository.findByIdNotDeleted(eq(globalTemplate.id!!))).thenReturn(globalTemplate)
        whenever(projectTemplateRepository.findByCode(eq("ORG"), eq(rootId), eq("global-tpl"))).thenReturn(null)
        // Use doAnswer to assign id so that toMap() can call doc.id!! after persist
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ProjectTemplateDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(projectTemplateRepository).persist(any<ProjectTemplateDocument>())

        // When
        val result = templateService.forkProjectTemplate(
            globalId = globalTemplate.id!!.toHexString(),
            rootOrgId = rootId.toHexString(),
            overrideCode = null,
            overrideName = null,
            actorId = actorId.toHexString()
        )

        // Then: verify return map has expected scope
        assertEquals("ORG", result["scope"])
        assertNotNull(result["id"])
    }

    // ─── INV-18: code uniqueness on fork ─────────────────────────────────────

    @Test
    fun `should reject fork when code already exists in ORG scope`() {
        // Given
        val globalTemplate = makeProjectTemplateDoc(scope = "GLOBAL", code = "dup-code")
        val existingOrgTemplate = makeProjectTemplateDoc(scope = "ORG", code = "dup-code", rootOrgId = rootId)
        whenever(projectTemplateRepository.findByIdNotDeleted(eq(globalTemplate.id!!))).thenReturn(globalTemplate)
        whenever(projectTemplateRepository.findByCode(eq("ORG"), eq(rootId), eq("dup-code"))).thenReturn(existingOrgTemplate)

        // When / Then
        assertThrows(ConflictException::class.java) {
            templateService.forkProjectTemplate(
                globalId = globalTemplate.id!!.toHexString(),
                rootOrgId = rootId.toHexString(),
                overrideCode = null,
                overrideName = null,
                actorId = actorId.toHexString()
            )
        }
    }

    // ─── INV-27: deactivate sets active=false ────────────────────────────────

    @Test
    fun `should set active=false when deactivateProjectTemplate called`() {
        // Given
        val template = makeProjectTemplateDoc(active = true)
        whenever(projectTemplateRepository.findByIdNotDeleted(eq(template.id!!))).thenReturn(template)
        val captured = argumentCaptor<ProjectTemplateDocument>()
        doNothing().whenever(projectTemplateRepository).update(captured.capture())

        // When
        templateService.deactivateProjectTemplate(template.id!!.toHexString())

        // Then
        assertFalse(captured.firstValue.active, "Template must be deactivated")
    }

    // ─── NotFoundException cases ──────────────────────────────────────────────

    @Test
    fun `should throw NotFoundException when project template not found`() {
        // Given
        whenever(projectTemplateRepository.findByIdNotDeleted(any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            templateService.getProjectTemplate(ObjectId().toHexString())
        }
    }

    @Test
    fun `should throw NotFoundException when task template not found`() {
        // Given
        whenever(taskTemplateRepository.findByIdNotDeleted(any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            templateService.getTaskTemplate(ObjectId().toHexString())
        }
    }

    @Test
    fun `should throw NotFoundException when fork source template not found`() {
        // Given
        whenever(projectTemplateRepository.findByIdNotDeleted(any())).thenReturn(null)

        // When / Then
        assertThrows(NotFoundException::class.java) {
            templateService.forkProjectTemplate(
                globalId = ObjectId().toHexString(),
                rootOrgId = rootId.toHexString(),
                overrideCode = null,
                overrideName = null,
                actorId = actorId.toHexString()
            )
        }
    }

    // ─── Template creation: scope derived from rootOrgId ─────────────────────

    @Test
    fun `should create GLOBAL scope template when rootOrgId is null`() {
        // Given: doAnswer assigns id so toMap() works after persist
        val captured = argumentCaptor<ProjectTemplateDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ProjectTemplateDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(projectTemplateRepository).persist(captured.capture())

        // When
        val result = templateService.createProjectTemplate(
            rootOrgId = null,  // GLOBAL scope
            body = mapOf("name" to "Global Template", "code" to "global-001"),
            actorId = actorId.toHexString()
        )

        // Then: verify the persisted document has GLOBAL scope
        assertEquals("GLOBAL", captured.firstValue.scope)
        assertNull(captured.firstValue.rootOrgId)
        assertEquals("GLOBAL", result["scope"])
    }

    @Test
    fun `should create ORG scope template when rootOrgId is provided`() {
        // Given
        val captured = argumentCaptor<ProjectTemplateDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<ProjectTemplateDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(projectTemplateRepository).persist(captured.capture())

        // When
        val result = templateService.createProjectTemplate(
            rootOrgId = rootId.toHexString(),
            body = mapOf("name" to "Org Template", "code" to "org-001"),
            actorId = actorId.toHexString()
        )

        // Then
        assertEquals("ORG", captured.firstValue.scope)
        assertEquals(rootId, captured.firstValue.rootOrgId)
        assertEquals("ORG", result["scope"])
    }

    @Test
    fun `should reject createProjectTemplate when name is missing`() {
        // When / Then
        assertThrows(ValidationException::class.java) {
            templateService.createProjectTemplate(
                rootOrgId = rootId.toHexString(),
                body = mapOf("code" to "no-name"),  // name missing
                actorId = actorId.toHexString()
            )
        }
    }

    // ─── Task Template: deactivate ────────────────────────────────────────────

    @Test
    fun `should set active=false when deactivateTaskTemplate called`() {
        // Given
        val template = makeTaskTemplateDoc(active = true)
        whenever(taskTemplateRepository.findByIdNotDeleted(eq(template.id!!))).thenReturn(template)
        val captured = argumentCaptor<TaskTemplateDocument>()
        doNothing().whenever(taskTemplateRepository).update(captured.capture())

        // When
        templateService.deactivateTaskTemplate(template.id!!.toHexString())

        // Then
        assertFalse(captured.firstValue.active)
    }

    // ─── Task Template fork: copies source metadata ───────────────────────────

    @Test
    fun `should fork task template and set forkedFrom metadata`() {
        // Given
        val sourceTemplate = makeTaskTemplateDoc(scope = "GLOBAL")
        whenever(taskTemplateRepository.findByIdNotDeleted(eq(sourceTemplate.id!!))).thenReturn(sourceTemplate)
        val captured = argumentCaptor<TaskTemplateDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<TaskTemplateDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(taskTemplateRepository).persist(captured.capture())

        // When
        templateService.forkTaskTemplate(
            globalId = sourceTemplate.id!!.toHexString(),
            rootOrgId = rootId.toHexString(),
            overrideCode = "forked-code",
            overrideName = "Forked Template",
            actorId = actorId.toHexString()
        )

        // Then
        val persisted = captured.firstValue
        assertEquals("ORG", persisted.scope)
        assertEquals("forked-code", persisted.code)
        assertEquals("Forked Template", persisted.name)
        assertNotNull(persisted.forkedFrom)
        assertEquals(sourceTemplate.id!!.toHexString(), persisted.forkedFrom!!.sourceId)
    }
}
