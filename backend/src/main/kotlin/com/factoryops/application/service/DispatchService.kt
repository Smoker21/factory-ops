package com.factoryops.application.service

import com.factoryops.domain.actionrequest.ActionRequest
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.ActionRequestDocument
import com.factoryops.persistence.document.SeverityDocument
import com.factoryops.persistence.mapper.ActionRequestMapper
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.repository.ActionRequestRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.time.Instant

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class DispatchService(
    private val actionRequestRepository: ActionRequestRepository,
    private val orgRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val taskService: TaskService,
    private val eventPublisherService: EventPublisherService
) {

    /**
     * Dispatches an ActionRequest from an upper org manager to a leaf org.
     * ADR-0008: Single-hop direct dispatch.
     * - targetOrg must be leaf
     * - actor must be manager of a target ancestor
     * - ownerId rules based on targetOrg.leaderIds count
     */
    fun dispatchActionRequest(
        targetOrgId: String,
        title: String,
        descriptionMarkdown: String?,
        severityLevel: SeverityLevel,
        severityReason: String?,
        ownerId: String?,
        actorId: String,
        actorOrgManagerScopes: List<String>,
        actorRootOrgId: String
    ): ActionRequest {
        val rootId = ObjectId(actorRootOrgId)
        val targetId = ObjectId(targetOrgId)

        val targetOrg = orgRepository.findByIdAndNotDeleted(targetId)
            ?: throw NotFoundException("Target organization not found: $targetOrgId")

        // Validate target is leaf
        if (!targetOrg.isLeaf) {
            throw ValidationException("Target organization must be a leaf type", "target_must_be_leaf")
        }

        // Validate actor is a manager of an ancestor of target (or target itself)
        val targetAncestorIds = targetOrg.ancestorIds.map { it.toHexString() } + targetOrgId
        val actorAncestorScope = actorOrgManagerScopes.firstOrNull { it in targetAncestorIds }
            ?: throw ForbiddenException("Actor is not authorized to dispatch to target organization", "not_authorized_to_dispatch")

        // Determine originatingOrgId (actor's manager scope closest ancestor of target)
        val originatingOrgId = actorAncestorScope

        // Determine ownerId based on leaderIds count (ADR-0010)
        val leaderIds = targetOrg.leaderIds.map { it.toHexString() }
        val resolvedOwnerId = when (leaderIds.size) {
            0 -> throw ConflictException("Target organization has no leaders for auto-assignment", "target_org_no_leader")
            1 -> leaderIds[0]
            else -> {
                if (ownerId == null) {
                    throw ValidationException("Must specify ownerId when target org has multiple leaders", "owner_must_be_specified")
                }
                if (!leaderIds.contains(ownerId)) {
                    throw ValidationException("ownerId must be one of the target org's leaders: $leaderIds", "owner_not_in_leaders")
                }
                ownerId
            }
        }

        val now = Instant.now()
        val doc = ActionRequestDocument().also { d ->
            d.rootOrgId = rootId
            d.originatingOrgId = ObjectId(originatingOrgId)
            d.targetOrgId = targetId
            d.title = title
            d.descriptionMarkdown = descriptionMarkdown
            d.severity = SeverityDocument().also { s -> s.level = severityLevel.name; s.reason = severityReason }
            d.status = ActionRequestStatus.SUBMITTED.name
            d.requesterId = ObjectId(actorId)
            d.ownerId = ObjectId(resolvedOwnerId)
            d.history = listOf(OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "ACTION_REQUEST_DISPATCHED", at = now,
                    payload = mapOf("originatingOrgId" to originatingOrgId, "targetOrgId" to targetOrgId))
            ))
            d.submittedAt = now
            d.createdAt = now
            d.updatedAt = now
        }
        actionRequestRepository.persist(doc)

        val ar = ActionRequestMapper.toDomain(doc)
        eventPublisherService.publishActionRequestDispatched(ar, actorId)
        logger.info { "Dispatched ActionRequest [${doc.id}] to org $targetOrgId" }
        return ar
    }

    fun listActionRequests(rootOrgId: String, status: String?, requesterId: String?): List<ActionRequest> {
        val rootId = ObjectId(rootOrgId)
        return when {
            status != null -> actionRequestRepository.findByStatus(rootId, status)
            else -> actionRequestRepository.findByRootOrgId(rootId)
        }.filter { requesterId == null || it.requesterId.toHexString() == requesterId }
            .map { ActionRequestMapper.toDomain(it) }
    }

    fun getActionRequest(id: String, rootOrgId: String): ActionRequest {
        return actionRequestRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?.let { ActionRequestMapper.toDomain(it) }
            ?: throw NotFoundException("ActionRequest not found: $id")
    }

    fun createActionRequest(
        rootOrgId: String,
        projectId: String?,
        originatingOrgId: String,
        targetOrgId: String,
        title: String,
        descriptionMarkdown: String?,
        severityLevel: SeverityLevel,
        severityReason: String?,
        actorId: String
    ): ActionRequest {
        val rootId = ObjectId(rootOrgId)

        val targetOrg = orgRepository.findByIdAndNotDeleted(ObjectId(targetOrgId))
            ?: throw NotFoundException("Target organization not found: $targetOrgId")

        if (!targetOrg.isLeaf) {
            throw ValidationException("Target organization must be a leaf type", "target_must_be_leaf")
        }

        val now = Instant.now()
        val doc = ActionRequestDocument().also { d ->
            d.rootOrgId = rootId
            d.projectId = projectId?.let { ObjectId(it) }
            d.originatingOrgId = ObjectId(originatingOrgId)
            d.targetOrgId = ObjectId(targetOrgId)
            d.title = title
            d.descriptionMarkdown = descriptionMarkdown
            d.severity = SeverityDocument().also { s -> s.level = severityLevel.name; s.reason = severityReason }
            d.status = ActionRequestStatus.SUBMITTED.name
            d.requesterId = ObjectId(actorId)
            d.history = listOf(OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "ACTION_REQUEST_CREATED", at = now)
            ))
            d.submittedAt = now
            d.createdAt = now
            d.updatedAt = now
        }
        actionRequestRepository.persist(doc)
        return ActionRequestMapper.toDomain(doc)
    }

    fun updateActionRequest(id: String, rootOrgId: String, title: String?, descriptionMarkdown: String?, actorId: String): ActionRequest {
        val doc = actionRequestRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?: throw NotFoundException("ActionRequest not found: $id")

        val now = Instant.now()
        title?.let { doc.title = it }
        descriptionMarkdown?.let { doc.descriptionMarkdown = it }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ACTION_REQUEST_UPDATED", at = now)
        )
        doc.updatedAt = now
        actionRequestRepository.update(doc)
        return ActionRequestMapper.toDomain(doc)
    }

    fun changeActionRequestStatus(id: String, rootOrgId: String, newStatus: ActionRequestStatus, reason: String?, actorId: String): ActionRequest {
        val doc = actionRequestRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?: throw NotFoundException("ActionRequest not found: $id")

        val now = Instant.now()
        val oldStatus = doc.status
        doc.status = newStatus.name
        if (newStatus == ActionRequestStatus.RESOLVED) {
            doc.resolvedAt = now
        }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ACTION_REQUEST_STATUS_CHANGED", at = now, payload = mapOf("from" to oldStatus, "to" to newStatus.name, "reason" to reason))
        )
        doc.updatedAt = now
        actionRequestRepository.update(doc)
        return ActionRequestMapper.toDomain(doc)
    }

    fun deleteActionRequest(id: String, rootOrgId: String, actorId: String) {
        val doc = actionRequestRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?: throw NotFoundException("ActionRequest not found: $id")

        doc.deletedAt = Instant.now()
        actionRequestRepository.update(doc)
    }

    fun convertToTask(
        actionRequestId: String,
        rootOrgId: String,
        projectId: String,
        taskType: String,
        taskTitle: String,
        actorId: String
    ): Task {
        val doc = actionRequestRepository.findByIdAndRootOrg(ObjectId(actionRequestId), ObjectId(rootOrgId))
            ?: throw NotFoundException("ActionRequest not found: $actionRequestId")

        if (doc.linkedTaskId != null) {
            throw ConflictException("ActionRequest already converted to a task", "already_converted")
        }

        val task = taskService.createTask(
            rootOrgId = rootOrgId,
            projectId = projectId,
            type = taskType,
            title = taskTitle,
            descriptionMarkdown = doc.descriptionMarkdown,
            priority = Priority.NORMAL,
            ownerId = doc.ownerId?.toHexString() ?: actorId,
            assignees = listOfNotNull(doc.ownerId?.toHexString()),
            attributes = emptyMap(),
            schedule = null,
            tags = emptyList(),
            actorId = actorId
        )

        doc.linkedTaskId = ObjectId(task.id!!)
        doc.status = ActionRequestStatus.TRIAGED.name
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ACTION_REQUEST_TRIAGED", at = Instant.now(), payload = mapOf("linkedTaskId" to task.id))
        )
        doc.updatedAt = Instant.now()
        actionRequestRepository.update(doc)

        return task
    }
}
