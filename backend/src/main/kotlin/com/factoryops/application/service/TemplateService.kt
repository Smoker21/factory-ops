package com.factoryops.application.service

import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.ForkRefDocument
import com.factoryops.persistence.document.ProjectTemplateDocument
import com.factoryops.persistence.document.TaskTemplateDocument
import com.factoryops.persistence.repository.ProjectTemplateRepository
import com.factoryops.persistence.repository.TaskTemplateRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * NOTE: @Transactional is applied at class level. MongoDB transactions require a replica set.
 * In dev/standalone mode Quarkus degrades to best-effort (no atomicity guarantee).
 */
@ApplicationScoped
@Transactional
class TemplateService(
    private val projectTemplateRepository: ProjectTemplateRepository,
    private val taskTemplateRepository: TaskTemplateRepository
) {

    // ========= Project Templates =========

    fun listGlobalProjectTemplates(): List<Map<String, Any?>> =
        projectTemplateRepository.findGlobalTemplates().map { it.toMap() }

    fun listOrgProjectTemplates(rootOrgId: String, includeGlobal: Boolean): List<Map<String, Any?>> =
        if (includeGlobal) {
            projectTemplateRepository.findByRootOrgIdIncludeGlobal(ObjectId(rootOrgId)).map { it.toMap() }
        } else {
            projectTemplateRepository.findByRootOrgId(ObjectId(rootOrgId)).map { it.toMap() }
        }

    fun getProjectTemplate(id: String): Map<String, Any?> {
        return projectTemplateRepository.findByIdNotDeleted(ObjectId(id))?.toMap()
            ?: throw NotFoundException("ProjectTemplate not found: $id")
    }

    fun createProjectTemplate(rootOrgId: String?, body: Map<String, Any?>, actorId: String): Map<String, Any?> {
        val now = Instant.now()
        val doc = ProjectTemplateDocument().also { d ->
            d.scope = if (rootOrgId == null) "GLOBAL" else "ORG"
            d.rootOrgId = rootOrgId?.let { ObjectId(it) }
            d.name = body["name"] as? String ?: throw ValidationException("name is required")
            d.code = body["code"] as? String ?: throw ValidationException("code is required")
            d.descriptionMarkdown = body["descriptionMarkdown"] as? String
            @Suppress("UNCHECKED_CAST")
            d.tags = (body["tags"] as? List<String>) ?: emptyList()
            d.active = true
            d.version = 1
            d.createdBy = ObjectId(actorId)
            d.createdAt = now
            d.updatedAt = now
        }
        projectTemplateRepository.persist(doc)
        logger.info { "Created ProjectTemplate [${doc.id}]" }
        return doc.toMap()
    }

    @Transactional
    fun updateProjectTemplate(id: String, body: Map<String, Any?>): Map<String, Any?> {
        val doc = projectTemplateRepository.findByIdNotDeleted(ObjectId(id))
            ?: throw NotFoundException("ProjectTemplate not found: $id")
        if (!doc.active) {
            throw BusinessRuleViolationException("Cannot update inactive template", "cannot_update_inactive_template")
        }
        (body["name"] as? String)?.let { doc.name = it }
        (body["descriptionMarkdown"] as? String)?.let { doc.descriptionMarkdown = it }
        doc.version = doc.version + 1
        doc.updatedAt = Instant.now()
        projectTemplateRepository.update(doc)
        logger.debug { "Updated ProjectTemplate [${doc.id}] to version=${doc.version}" }
        return doc.toMap()
    }

    fun deactivateProjectTemplate(id: String) {
        val doc = projectTemplateRepository.findByIdNotDeleted(ObjectId(id))
            ?: throw NotFoundException("ProjectTemplate not found: $id")
        doc.active = false
        doc.updatedAt = Instant.now()
        projectTemplateRepository.update(doc)
    }

    fun forkProjectTemplate(globalId: String, rootOrgId: String, overrideCode: String?, overrideName: String?, actorId: String): Map<String, Any?> {
        val source = projectTemplateRepository.findByIdNotDeleted(ObjectId(globalId))
            ?: throw NotFoundException("Source ProjectTemplate not found: $globalId")
        if (source.scope != "GLOBAL") {
            throw ValidationException("Can only fork from GLOBAL templates", "not_global")
        }

        val code = overrideCode ?: source.code
        val existing = projectTemplateRepository.findByCode("ORG", ObjectId(rootOrgId), code)
        if (existing != null) {
            throw ConflictException("ProjectTemplate with code '$code' already exists in org", "code_duplicate")
        }

        val now = Instant.now()
        val doc = ProjectTemplateDocument().also { d ->
            d.scope = "ORG"
            d.rootOrgId = ObjectId(rootOrgId)
            d.name = overrideName ?: source.name
            d.code = code
            d.descriptionMarkdown = source.descriptionMarkdown
            d.tags = source.tags
            d.active = true
            d.version = 1
            d.createdBy = ObjectId(actorId)
            d.forkedFrom = ForkRefDocument().also { f -> f.sourceScope = "GLOBAL"; f.sourceId = globalId; f.sourceVersion = source.version; f.forkedAt = now }
            d.createdAt = now
            d.updatedAt = now
        }
        projectTemplateRepository.persist(doc)
        return doc.toMap()
    }

    // ========= Task Templates =========

    fun listGlobalTaskTemplates(): List<Map<String, Any?>> =
        taskTemplateRepository.findGlobalTemplates().map { it.toMap() }

    fun listOrgTaskTemplates(rootOrgId: String, includeGlobal: Boolean): List<Map<String, Any?>> =
        if (includeGlobal) {
            taskTemplateRepository.findByRootOrgIdIncludeGlobal(ObjectId(rootOrgId)).map { it.toMap() }
        } else {
            taskTemplateRepository.findByRootOrgId(ObjectId(rootOrgId)).map { it.toMap() }
        }

    fun getTaskTemplate(id: String): Map<String, Any?> {
        return taskTemplateRepository.findByIdNotDeleted(ObjectId(id))?.toMap()
            ?: throw NotFoundException("TaskTemplate not found: $id")
    }

    fun createTaskTemplate(rootOrgId: String?, body: Map<String, Any?>, actorId: String): Map<String, Any?> {
        val now = Instant.now()
        val doc = TaskTemplateDocument().also { d ->
            d.scope = if (rootOrgId == null) "GLOBAL" else "ORG"
            d.rootOrgId = rootOrgId?.let { ObjectId(it) }
            d.name = body["name"] as? String ?: throw ValidationException("name is required")
            d.code = body["code"] as? String ?: throw ValidationException("code is required")
            d.type = body["type"] as? String ?: "GENERAL"
            d.defaultPriority = body["defaultPriority"] as? String ?: "NORMAL"
            @Suppress("UNCHECKED_CAST")
            d.tags = (body["tags"] as? List<String>) ?: emptyList()
            d.active = true
            d.version = 1
            d.createdBy = ObjectId(actorId)
            d.createdAt = now
            d.updatedAt = now
        }
        taskTemplateRepository.persist(doc)
        return doc.toMap()
    }

    @Transactional
    fun updateTaskTemplate(id: String, body: Map<String, Any?>): Map<String, Any?> {
        val doc = taskTemplateRepository.findByIdNotDeleted(ObjectId(id))
            ?: throw NotFoundException("TaskTemplate not found: $id")
        if (!doc.active) {
            throw BusinessRuleViolationException("Cannot update inactive template", "cannot_update_inactive_template")
        }
        (body["name"] as? String)?.let { doc.name = it }
        (body["defaultPriority"] as? String)?.let { doc.defaultPriority = it }
        doc.version = doc.version + 1
        doc.updatedAt = Instant.now()
        taskTemplateRepository.update(doc)
        logger.debug { "Updated TaskTemplate [${doc.id}] to version=${doc.version}" }
        return doc.toMap()
    }

    fun deactivateTaskTemplate(id: String) {
        val doc = taskTemplateRepository.findByIdNotDeleted(ObjectId(id))
            ?: throw NotFoundException("TaskTemplate not found: $id")
        doc.active = false
        doc.updatedAt = Instant.now()
        taskTemplateRepository.update(doc)
    }

    fun forkTaskTemplate(globalId: String, rootOrgId: String, overrideCode: String?, overrideName: String?, actorId: String): Map<String, Any?> {
        val source = taskTemplateRepository.findByIdNotDeleted(ObjectId(globalId))
            ?: throw NotFoundException("Source TaskTemplate not found: $globalId")

        val now = Instant.now()
        val doc = TaskTemplateDocument().also { d ->
            d.scope = "ORG"
            d.rootOrgId = ObjectId(rootOrgId)
            d.name = overrideName ?: source.name
            d.code = overrideCode ?: source.code
            d.type = source.type
            d.defaultPriority = source.defaultPriority
            d.defaultAttributes = source.defaultAttributes
            d.tags = source.tags
            d.active = true
            d.version = 1
            d.createdBy = ObjectId(actorId)
            d.forkedFrom = ForkRefDocument().also { f -> f.sourceScope = "GLOBAL"; f.sourceId = globalId; f.sourceVersion = source.version; f.forkedAt = now }
            d.createdAt = now
            d.updatedAt = now
        }
        taskTemplateRepository.persist(doc)
        return doc.toMap()
    }

    private fun ProjectTemplateDocument.toMap(): Map<String, Any?> = mapOf(
        "id" to id!!.toHexString(),
        "scope" to scope,
        "rootOrgId" to rootOrgId?.toHexString(),
        "name" to name,
        "code" to code,
        "descriptionMarkdown" to descriptionMarkdown,
        "tags" to tags,
        "version" to version,
        "active" to active,
        "createdAt" to createdAt.toString(),
        "updatedAt" to updatedAt.toString()
    )

    private fun TaskTemplateDocument.toMap(): Map<String, Any?> = mapOf(
        "id" to id!!.toHexString(),
        "scope" to scope,
        "rootOrgId" to rootOrgId?.toHexString(),
        "name" to name,
        "code" to code,
        "type" to type,
        "defaultPriority" to defaultPriority,
        "tags" to tags,
        "version" to version,
        "active" to active,
        "createdAt" to createdAt.toString(),
        "updatedAt" to updatedAt.toString()
    )
}
