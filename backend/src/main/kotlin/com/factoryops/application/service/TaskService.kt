package com.factoryops.application.service

import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.task.Comment
import com.factoryops.domain.task.QaReview
import com.factoryops.domain.task.QaReviewPolicy
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.Task
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ForbiddenException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.TaskDocument
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.mapper.TaskMapper
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.TaskRepository
import com.factoryops.persistence.repository.UserRepository
import com.github.f4b6a3.ulid.UlidCreator
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
class TaskService(
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val groupRepository: GroupRepository,
    private val groupMembershipRepository: GroupMembershipRepository,
    private val userRepository: UserRepository,
    private val eventPublisherService: EventPublisherService
) {

    fun listTasks(
        rootOrgId: String,
        projectId: String?,
        status: String?,
        ownerId: String?,
        assigneeId: String?,
        type: String?,
        cursor: String? = null,
        limit: Int = 20
    ): Pair<List<Task>, com.factoryops.interfaces.dto.PageInfo> {
        val rootId = ObjectId(rootOrgId)
        val pageSize = limit.coerceIn(1, 100)
        val cursorId = cursor?.let { runCatching { ObjectId(it) }.getOrNull() }

        val docs = when {
            projectId != null || ownerId != null || assigneeId != null || status != null || type != null -> {
                when {
                    projectId != null -> taskRepository.findByProjectId(rootId, ObjectId(projectId))
                    ownerId != null -> taskRepository.findByOwnerId(rootId, ObjectId(ownerId))
                    assigneeId != null -> taskRepository.findByAssigneeId(rootId, ObjectId(assigneeId))
                    else -> taskRepository.findByStatus(rootId, status!!)
                }.filter { type == null || it.type == type }
            }
            else -> taskRepository.findWithCursor(rootId, cursorId, pageSize + 1)
        }

        val hasMore = docs.size > pageSize
        val items = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null

        return items.map { TaskMapper.toDomain(it) } to com.factoryops.interfaces.dto.PageInfo(nextCursor, hasMore)
    }

    fun getTask(taskId: String, rootOrgId: String): Task {
        return taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?.let { TaskMapper.toDomain(it) }
            ?: throw NotFoundException("Task not found: $taskId")
    }

    fun createTask(
        rootOrgId: String,
        projectId: String,
        type: String,
        title: String,
        descriptionMarkdown: String?,
        priority: Priority,
        ownerId: String,
        assignees: List<String>,
        attributes: Map<String, Any?>,
        schedule: TimeRange?,
        tags: List<String>,
        actorId: String,
        originActionRequestId: String? = null
    ): Task {
        val rootId = ObjectId(rootOrgId)

        val projectDoc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), rootId)
            ?: throw NotFoundException("Project not found: $projectId")

        // C-011: validate task dueAt >= project.startAt (INV-5)
        val projectStartAt = projectDoc.schedule?.start
        if (schedule != null && schedule.due != null && projectStartAt != null) {
            if (schedule.due < projectStartAt) {
                throw ValidationException("Task due date must not be before project start date", "invalid_due_date")
            }
        }

        // Validate ownerId ∈ assignees (INV-1)
        val allAssignees = (assignees + ownerId).distinct()

        // Build QA review policy snapshot from project's groups
        val qaPolicy = buildQaReviewPolicy(rootOrgId, projectDoc.groupIds.map { it.toHexString() })

        val now = Instant.now()
        val doc = TaskDocument().also { d ->
            d.rootOrgId = rootId
            d.projectId = ObjectId(projectId)
            d.type = type
            d.title = title
            d.descriptionMarkdown = descriptionMarkdown
            d.status = TaskStatus.OPEN.name
            d.priority = priority.name
            d.ownerId = ObjectId(ownerId)
            d.assignees = allAssignees.map { ObjectId(it) }
            d.originActionRequestId = originActionRequestId?.let { ObjectId(it) }
            d.attributes = attributes
            d.schedule = schedule?.let { com.factoryops.persistence.document.TimeRangeDocument().also { r -> r.start = it.start; r.due = it.due } }
            d.tags = tags
            d.qaReviewPolicy = TaskMapper.run {
                com.factoryops.persistence.document.QaReviewPolicyDocument().also { p ->
                    p.dualSignRequired = qaPolicy.dualSignRequired
                    p.requiredReviewerRoles = qaPolicy.requiredReviewerRoles.map { it.name }
                    p.snapshotAt = qaPolicy.snapshotAt
                    p.sourceGroupIds = qaPolicy.sourceGroupIds.map { ObjectId(it) }
                }
            }
            d.history = listOf(OrganizationMapper.auditToDocument(AuditEntry(actorId = actorId, action = "TASK_CREATED", at = now)))
            d.createdAt = now
            d.createdBy = ObjectId(actorId)
            d.updatedAt = now
        }
        taskRepository.persist(doc)

        val task = TaskMapper.toDomain(doc)
        eventPublisherService.publishTaskCreated(task, actorId)
        logger.info { "Created task [${doc.id}] title=$title type=$type" }
        return task
    }

    private fun buildQaReviewPolicy(rootOrgId: String, groupIds: List<String>): QaReviewPolicy {
        val rootId = ObjectId(rootOrgId)
        // C-007: mapNotNull replaced with explicit 404 — silent ignore leads to wrong policy snapshot
        val groups = groupIds.map { gid ->
            groupRepository.findByIdAndRootOrg(ObjectId(gid), rootId)
                ?: throw NotFoundException("Group $gid not found when building QA review policy")
        }
        val dualSignRequired = groups.any { it.settings.qa.dualSignRequired }
        val requiredRoles = groups
            .flatMap { it.settings.qa.requiredReviewerRoles }
            .mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
            .distinct()

        return QaReviewPolicy(
            dualSignRequired = dualSignRequired,
            requiredReviewerRoles = requiredRoles,
            snapshotAt = Instant.now(),
            sourceGroupIds = groupIds
        )
    }

    fun updateTask(taskId: String, rootOrgId: String, title: String?, descriptionMarkdown: String?, priority: Priority?, attributes: Map<String, Any?>?, schedule: TimeRange?, tags: List<String>?, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val now = Instant.now()
        title?.let { doc.title = it }
        descriptionMarkdown?.let { doc.descriptionMarkdown = it }
        priority?.let { doc.priority = it.name }
        attributes?.let { doc.attributes = it }
        tags?.let { doc.tags = it }
        schedule?.let { doc.schedule = com.factoryops.persistence.document.TimeRangeDocument().also { r -> r.start = it.start; r.due = it.due } }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_UPDATED", at = now)
        )
        doc.updatedAt = now
        taskRepository.update(doc)
        return TaskMapper.toDomain(doc)
    }

    fun changeTaskStatus(taskId: String, rootOrgId: String, newStatus: TaskStatus, reason: String?, actorRoles: List<String>, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val current = TaskStatus.valueOf(doc.status)

        // QA dual sign check: IN_PROGRESS -> DONE only allowed for GROUP_MANAGER (force-complete)
        if (current == TaskStatus.IN_PROGRESS && newStatus == TaskStatus.DONE && doc.qaReviewPolicy.dualSignRequired) {
            if (!actorRoles.contains("GROUP_MANAGER")) {
                throw BusinessRuleViolationException(
                    "Task requires QA dual sign review before DONE. Use IN_REVIEW flow or force-complete with GROUP_MANAGER role.",
                    "qa_review_required"
                )
            }
            if (reason.isNullOrBlank()) {
                throw ValidationException("reason (bypassReason) is required for force-complete with dualSignRequired task", "reason_required")
            }
            // C-006: actor must be an active member of at least one of the task's groups
            val rootId = doc.rootOrgId
            val isMember = doc.qaReviewPolicy.sourceGroupIds.any { groupId ->
                groupMembershipRepository.findActiveByGroupIdAndUserId(rootId, groupId, ObjectId(actorId)) != null
            }
            if (!isMember) {
                throw ForbiddenException(
                    "Actor must be an active member of one of the task's groups to force-complete",
                    "not_group_member"
                )
            }
        }

        validateTaskStatusTransition(current, newStatus)

        val now = Instant.now()
        val bypassed = actorRoles.contains("GROUP_MANAGER") && doc.qaReviewPolicy.dualSignRequired
        if (newStatus == TaskStatus.DONE) {
            // C-008: use shared transitionToDone helper for consistent history/event
            applyDoneTransition(doc, actorId, now, via = if (bypassed) "force_complete" else "status_change", bypassed = bypassed, reason = reason, from = current.name)
        } else {
            doc.status = newStatus.name
            doc.history = doc.history + OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "TASK_STATUS_CHANGED", at = now, payload = mapOf("from" to current.name, "to" to newStatus.name, "reason" to reason))
            )
        }
        doc.updatedAt = now
        taskRepository.update(doc)

        val task = TaskMapper.toDomain(doc)
        if (newStatus == TaskStatus.DONE) {
            eventPublisherService.publishTaskCompleted(task, actorId)
        }
        return task
    }

    private fun validateTaskStatusTransition(from: TaskStatus, to: TaskStatus) {
        val allowed = mapOf(
            TaskStatus.OPEN to setOf(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS to setOf(TaskStatus.BLOCKED, TaskStatus.IN_REVIEW, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.BLOCKED to setOf(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_REVIEW to setOf(TaskStatus.IN_PROGRESS, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.DONE to emptySet(),
            TaskStatus.CANCELLED to emptySet()
        )
        if (!allowed[from]!!.contains(to)) {
            throw StateTransitionException("Invalid task status transition from $from to $to")
        }
    }

    /**
     * C-008: Unified DONE transition. Both auto-complete (qa_review_complete) and manual/force paths
     * call this method to guarantee consistent history entries and event emission.
     */
    private fun applyDoneTransition(
        doc: com.factoryops.persistence.document.TaskDocument,
        actorId: String,
        now: Instant,
        via: String,
        bypassed: Boolean,
        reason: String? = null,
        from: String = TaskStatus.IN_REVIEW.name
    ) {
        doc.status = TaskStatus.DONE.name
        doc.completedAt = now
        val payload = mutableMapOf<String, Any?>(
            "from" to from,
            "to" to TaskStatus.DONE.name,
            "via" to via,
            "bypassed" to bypassed
        )
        if (reason != null) payload["reason"] = reason
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_STATUS_CHANGED", at = now, payload = payload)
        )
    }

    fun addAssignees(taskId: String, rootOrgId: String, userIds: List<String>, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        // C-012: all new assignees must be active users in the same rootOrgId
        val rootId = ObjectId(rootOrgId)
        val invalidUserIds = userIds.filter { uid ->
            val userDoc = userRepository.findByIdAndNotDeleted(ObjectId(uid), rootId)
            userDoc == null || !userDoc.active
        }
        if (invalidUserIds.isNotEmpty()) {
            throw ValidationException(
                "The following user IDs are not active or not found in this organization: $invalidUserIds",
                "invalid_assignees"
            )
        }

        val newAssignees = (doc.assignees + userIds.map { ObjectId(it) }).distinct()
        val now = Instant.now()
        doc.assignees = newAssignees
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_ASSIGNEES_ADDED", at = now, payload = mapOf("userIds" to userIds))
        )
        doc.updatedAt = now
        taskRepository.update(doc)

        val task = TaskMapper.toDomain(doc)
        eventPublisherService.publishTaskAssigned(task, actorId)
        return task
    }

    fun removeAssignee(taskId: String, rootOrgId: String, userId: String, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        if (doc.ownerId.toHexString() == userId) {
            throw ConflictException("Cannot remove task owner from assignees. Transfer ownership first.", "remove_owner_forbidden")
        }

        val now = Instant.now()
        doc.assignees = doc.assignees.filter { it.toHexString() != userId }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_ASSIGNEE_REMOVED", at = now, payload = mapOf("userId" to userId))
        )
        doc.updatedAt = now
        taskRepository.update(doc)
        return TaskMapper.toDomain(doc)
    }

    fun transferTaskOwner(taskId: String, rootOrgId: String, newOwnerId: String, keepPreviousOwnerAsAssignee: Boolean, reason: String?, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val now = Instant.now()
        val oldOwnerId = doc.ownerId.toHexString()
        val newOwnerOid = ObjectId(newOwnerId)

        // New owner must be in assignees or will be added
        var newAssignees = doc.assignees.toMutableList()
        if (!newAssignees.contains(newOwnerOid)) {
            newAssignees.add(newOwnerOid)
        }
        if (!keepPreviousOwnerAsAssignee) {
            newAssignees = newAssignees.filter { it.toHexString() != oldOwnerId }.toMutableList()
        }

        doc.ownerId = newOwnerOid
        doc.assignees = newAssignees
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_OWNER_TRANSFERRED", at = now, payload = mapOf("from" to oldOwnerId, "to" to newOwnerId, "reason" to reason))
        )
        doc.updatedAt = now
        taskRepository.update(doc)

        val task = TaskMapper.toDomain(doc)
        eventPublisherService.publishTaskOwnerTransferred(task, actorId)
        return task
    }

    fun submitReview(taskId: String, rootOrgId: String, reviewerRole: Role, decision: ReviewDecision, reason: String?, actorId: String): Task {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val currentStatus = TaskStatus.valueOf(doc.status)
        if (currentStatus != TaskStatus.IN_REVIEW) {
            throw ConflictException("Task must be IN_REVIEW status to submit a review", "wrong_status")
        }
        if (!doc.qaReviewPolicy.dualSignRequired) {
            throw ConflictException("Task does not require QA dual sign review", "dual_sign_not_required")
        }

        // Validate role is in required roles
        if (!doc.qaReviewPolicy.requiredReviewerRoles.contains(reviewerRole.name)) {
            throw ForbiddenException("Role $reviewerRole is not required for this task's review policy", "role_not_required")
        }

        // Check for duplicate review (INV-36)
        val existingReview = doc.qaReviews.find { it.reviewerId == ObjectId(actorId) && it.reviewerRole == reviewerRole.name }
        if (existingReview != null) {
            throw ConflictException("Review already submitted by this reviewer with role $reviewerRole", "review_already_submitted")
        }

        val now = Instant.now()
        val review = com.factoryops.persistence.document.QaReviewDocument().also { r ->
            r.reviewerId = ObjectId(actorId)
            r.reviewerRole = reviewerRole.name
            r.decision = decision.name
            r.reason = reason
            r.at = now
        }

        if (decision == ReviewDecision.REJECTED) {
            // Q-21: reject clears all reviews and reverts to IN_PROGRESS (ADR-0011 v1.4 Amendment §5)
            doc.qaReviews = emptyList()
            doc.status = TaskStatus.IN_PROGRESS.name
            doc.history = doc.history + OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "TASK_REVIEW_REJECTED", at = now, payload = mapOf("role" to reviewerRole.name, "reason" to reason))
            )
        } else {
            doc.qaReviews = doc.qaReviews + review
            doc.history = doc.history + OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "TASK_REVIEW_APPROVED", at = now, payload = mapOf("role" to reviewerRole.name))
            )

            // Q-23 OR: auto-complete when enough reviews have been collected.
            // ADR-0011 v1.4 Amendment §2 formula:
            //   auto_complete = qaReviews.size >= 2 AND all reviews are in whitelist AND all are APPROVED
            // dualSignRequired is always true here because the early guard above throws when it's false.
            // The whitelist is enforced at write time (role guard above).
            // REJECTED clears qaReviews, so surviving reviews are always APPROVED — size check suffices.
            val requiredCount = if (doc.qaReviewPolicy.dualSignRequired) 2 else 1  // else=unreachable (guard above)
            if (doc.qaReviews.size >= requiredCount) {
                // C-008: shared transitionToDone helper ensures consistent history/event across both paths
                applyDoneTransition(doc, actorId, now, via = "qa_review_complete", bypassed = false)
            }
        }

        doc.updatedAt = now
        taskRepository.update(doc)

        val task = TaskMapper.toDomain(doc)
        if (task.status == TaskStatus.DONE) {
            eventPublisherService.publishTaskCompleted(task, actorId)
        }
        return task
    }

    fun deleteTask(taskId: String, rootOrgId: String, actorId: String) {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        doc.deletedAt = Instant.now()
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "TASK_DELETED", at = Instant.now())
        )
        taskRepository.update(doc)
    }

    fun listComments(taskId: String, rootOrgId: String): List<Comment> {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")
        return doc.comments.filter { it.deletedAt == null }.map { c ->
            Comment(
                id = c.id,
                authorId = c.authorId.toHexString(),
                bodyMarkdown = c.bodyMarkdown,
                attachments = emptyList(),
                createdAt = c.createdAt,
                editedAt = c.editedAt,
                deletedAt = c.deletedAt
            )
        }
    }

    fun addComment(taskId: String, rootOrgId: String, bodyMarkdown: String, actorId: String): Comment {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val now = Instant.now()
        val commentDoc = com.factoryops.persistence.document.CommentDocument().also { c ->
            c.id = UlidCreator.getUlid().toString()
            c.authorId = ObjectId(actorId)
            c.bodyMarkdown = bodyMarkdown
            c.createdAt = now
        }
        doc.comments = doc.comments + commentDoc
        doc.updatedAt = now
        taskRepository.update(doc)

        return Comment(
            id = commentDoc.id,
            authorId = actorId,
            bodyMarkdown = bodyMarkdown,
            createdAt = now
        )
    }

    fun editComment(taskId: String, rootOrgId: String, commentId: String, bodyMarkdown: String, actorId: String): Comment {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val comment = doc.comments.find { it.id == commentId }
            ?: throw NotFoundException("Comment not found: $commentId")

        if (comment.authorId.toHexString() != actorId) {
            throw ForbiddenException("Only the comment author can edit the comment", "not_author")
        }

        val now = Instant.now()
        val updated = comment.also { it.bodyMarkdown = bodyMarkdown; it.editedAt = now }
        doc.comments = doc.comments.map { if (it.id == commentId) updated else it }
        doc.updatedAt = now
        taskRepository.update(doc)

        return Comment(id = commentId, authorId = actorId, bodyMarkdown = bodyMarkdown, editedAt = now, createdAt = comment.createdAt)
    }

    fun deleteComment(taskId: String, rootOrgId: String, commentId: String, actorId: String) {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")

        val comment = doc.comments.find { it.id == commentId }
            ?: throw NotFoundException("Comment not found: $commentId")

        val now = Instant.now()
        comment.deletedAt = now
        doc.comments = doc.comments.map { if (it.id == commentId) comment else it }
        doc.updatedAt = now
        taskRepository.update(doc)
    }

    fun getTaskHistory(taskId: String, rootOrgId: String): List<AuditEntry> {
        val doc = taskRepository.findByIdAndRootOrg(ObjectId(taskId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Task not found: $taskId")
        return doc.history.map { OrganizationMapper.auditToDomain(it) }
    }
}
