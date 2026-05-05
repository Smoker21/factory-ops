package com.factoryops.application.service

import com.factoryops.domain.membership.Membership
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.Project
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.TimeRange
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.StateTransitionException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.mapper.ProjectMapper
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.MembershipRepository
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.ProjectRepository
import com.factoryops.persistence.repository.UserRepository
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
class ProjectService(
    private val projectRepository: ProjectRepository,
    private val membershipRepository: MembershipRepository,
    private val orgRepository: OrganizationRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) {

    fun listProjects(
        rootOrgId: String,
        status: String?,
        ownerId: String?,
        memberId: String?,
        groupId: String?,
        cursor: String? = null,
        limit: Int = 20
    ): Pair<List<Project>, PageInfo> {
        val rootId = ObjectId(rootOrgId)
        val pageSize = limit.coerceIn(1, 100)
        val cursorId = cursor?.let { runCatching { ObjectId(it) }.getOrNull() }

        val docs = when {
            groupId != null || ownerId != null || memberId != null || status != null -> {
                // Filtered queries fall back to non-cursor (complex filters)
                when {
                    groupId != null -> projectRepository.findByGroupId(rootId, ObjectId(groupId))
                    ownerId != null -> projectRepository.findByOwnerId(rootId, ObjectId(ownerId))
                    memberId != null -> projectRepository.findByMemberId(rootId, ObjectId(memberId))
                    else -> projectRepository.findByStatus(rootId, status!!)
                }
            }
            else -> projectRepository.findWithCursor(rootId, cursorId, pageSize + 1)
        }

        val hasMore = docs.size > pageSize
        val items = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null

        return items.map { ProjectMapper.toDomain(it) } to PageInfo(nextCursor, hasMore)
    }

    fun getProject(projectId: String, rootOrgId: String): Project {
        return projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?.let { ProjectMapper.toDomain(it) }
            ?: throw NotFoundException("Project not found: $projectId")
    }

    fun createProject(
        rootOrgId: String,
        organizationId: String,
        name: String,
        descriptionMarkdown: String?,
        ownerId: String,
        memberIds: List<String>,
        groupIds: List<String>,
        schedule: TimeRange?,
        tags: List<String>,
        actorId: String
    ): Project {
        val rootId = ObjectId(rootOrgId)

        val orgDoc = orgRepository.findByIdAndRootOrg(ObjectId(organizationId), rootId)
            ?: throw NotFoundException("Organization not found: $organizationId")
        if (!orgDoc.isLeaf) {
            throw ValidationException("Organization must be a leaf type to contain projects", "org_not_leaf")
        }

        // Validate all groups are in the same leaf org (INV-19)
        if (groupIds.isEmpty()) {
            throw ValidationException("Project must have at least one group", "groups_required")
        }
        for (gid in groupIds) {
            val group = groupRepository.findByIdAndRootOrg(ObjectId(gid), rootId)
                ?: throw NotFoundException("Group not found: $gid")
            if (group.organizationId.toHexString() != organizationId) {
                throw ValidationException("Group $gid does not belong to organization $organizationId", "group_wrong_org")
            }
        }

        // Ensure ownerId ∈ memberIds
        val allMemberIds = (memberIds + ownerId).distinct()

        val now = Instant.now()
        val doc = ProjectDocument().also { d ->
            d.rootOrgId = rootId
            d.organizationId = ObjectId(organizationId)
            d.name = name
            d.descriptionMarkdown = descriptionMarkdown
            d.status = ProjectStatus.DRAFT.name
            d.ownerId = ObjectId(ownerId)
            d.memberIds = allMemberIds.map { ObjectId(it) }
            d.groupIds = groupIds.map { ObjectId(it) }
            d.schedule = schedule?.let { com.factoryops.persistence.document.TimeRangeDocument().also { r -> r.start = it.start; r.due = it.due } }
            d.tags = tags
            d.history = listOf(OrganizationMapper.auditToDocument(AuditEntry(actorId = actorId, action = "PROJECT_CREATED", at = now)))
            d.createdAt = now
            d.updatedAt = now
        }
        projectRepository.persist(doc)

        // Persist memberships
        for (uid in allMemberIds) {
            val memDoc = MembershipDocument().also { m ->
                m.rootOrgId = rootId
                m.projectId = doc.id!!
                m.userId = ObjectId(uid)
                m.role = if (uid == ownerId) MembershipRole.LEAD.name else MembershipRole.MEMBER.name
                m.joinedAt = now
            }
            membershipRepository.persist(memDoc)
        }

        logger.info { "Created project [${doc.id}] name=$name" }
        return ProjectMapper.toDomain(doc)
    }

    fun updateProject(projectId: String, rootOrgId: String, name: String?, descriptionMarkdown: String?, schedule: TimeRange?, tags: List<String>?, actorId: String): Project {
        val doc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Project not found: $projectId")

        val now = Instant.now()
        name?.let { doc.name = it }
        descriptionMarkdown?.let { doc.descriptionMarkdown = it }
        tags?.let { doc.tags = it }
        schedule?.let { doc.schedule = com.factoryops.persistence.document.TimeRangeDocument().also { r -> r.start = it.start; r.due = it.due } }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "PROJECT_UPDATED", at = now)
        )
        doc.updatedAt = now
        projectRepository.update(doc)
        return ProjectMapper.toDomain(doc)
    }

    fun changeProjectStatus(projectId: String, rootOrgId: String, newStatus: ProjectStatus, reason: String?, actorId: String): Project {
        val doc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Project not found: $projectId")

        val current = ProjectStatus.valueOf(doc.status)
        validateProjectStatusTransition(current, newStatus)

        val now = Instant.now()
        doc.status = newStatus.name
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "PROJECT_STATUS_CHANGED", at = now, payload = mapOf("from" to current.name, "to" to newStatus.name, "reason" to reason))
        )
        doc.updatedAt = now
        projectRepository.update(doc)
        return ProjectMapper.toDomain(doc)
    }

    private fun validateProjectStatusTransition(from: ProjectStatus, to: ProjectStatus) {
        val allowed = mapOf(
            ProjectStatus.DRAFT to setOf(ProjectStatus.ACTIVE, ProjectStatus.CANCELLED),
            ProjectStatus.ACTIVE to setOf(ProjectStatus.PAUSED, ProjectStatus.COMPLETED, ProjectStatus.CANCELLED),
            ProjectStatus.PAUSED to setOf(ProjectStatus.ACTIVE, ProjectStatus.CANCELLED),
            ProjectStatus.COMPLETED to emptySet(),
            ProjectStatus.CANCELLED to emptySet()
        )
        if (!allowed[from]!!.contains(to)) {
            throw StateTransitionException("Invalid project status transition from $from to $to")
        }
    }

    fun changeProjectOwner(projectId: String, rootOrgId: String, newOwnerId: String, reason: String?, actorId: String): Project {
        val doc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Project not found: $projectId")

        val newOwnerOid = ObjectId(newOwnerId)
        if (!doc.memberIds.contains(newOwnerOid)) {
            doc.memberIds = doc.memberIds + newOwnerOid
            val now = Instant.now()
            val memDoc = MembershipDocument().also { m ->
                m.rootOrgId = ObjectId(rootOrgId)
                m.projectId = doc.id!!
                m.userId = newOwnerOid
                m.role = MembershipRole.LEAD.name
                m.joinedAt = now
            }
            membershipRepository.persist(memDoc)
        }

        val now = Instant.now()
        val oldOwnerId = doc.ownerId.toHexString()
        doc.ownerId = newOwnerOid
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "PROJECT_OWNER_CHANGED", at = now, payload = mapOf("from" to oldOwnerId, "to" to newOwnerId, "reason" to reason))
        )
        doc.updatedAt = now
        projectRepository.update(doc)
        return ProjectMapper.toDomain(doc)
    }

    fun deleteProject(projectId: String, rootOrgId: String, actorId: String) {
        val doc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Project not found: $projectId")

        doc.deletedAt = Instant.now()
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "PROJECT_DELETED", at = Instant.now())
        )
        projectRepository.update(doc)
    }

    fun listMembers(projectId: String, rootOrgId: String): List<Membership> {
        return membershipRepository.findByProjectId(ObjectId(rootOrgId), ObjectId(projectId))
            .map { ProjectMapper.membershipToDomain(it) }
    }

    fun addMember(projectId: String, rootOrgId: String, userId: String, role: MembershipRole, actorId: String): Membership {
        val rootId = ObjectId(rootOrgId)
        projectRepository.findByIdAndRootOrg(ObjectId(projectId), rootId)
            ?: throw NotFoundException("Project not found: $projectId")

        userRepository.findByIdAndNotDeleted(ObjectId(userId), rootId)
            ?: throw NotFoundException("User not found: $userId")

        val existing = membershipRepository.findByProjectIdAndUserId(rootId, ObjectId(projectId), ObjectId(userId))
        if (existing != null) {
            throw ConflictException("User is already a member of this project", "already_member")
        }

        val now = Instant.now()
        val doc = MembershipDocument().also { m ->
            m.rootOrgId = rootId
            m.projectId = ObjectId(projectId)
            m.userId = ObjectId(userId)
            m.role = role.name
            m.joinedAt = now
        }
        membershipRepository.persist(doc)

        // Update project.memberIds
        val projectDoc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), rootId)!!
        if (!projectDoc.memberIds.contains(ObjectId(userId))) {
            projectDoc.memberIds = projectDoc.memberIds + ObjectId(userId)
            projectDoc.updatedAt = now
            projectRepository.update(projectDoc)
        }

        return ProjectMapper.membershipToDomain(doc)
    }

    fun removeMember(projectId: String, rootOrgId: String, userId: String, actorId: String) {
        val rootId = ObjectId(rootOrgId)
        val projectDoc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), rootId)
            ?: throw NotFoundException("Project not found: $projectId")

        if (projectDoc.ownerId.toHexString() == userId) {
            throw ConflictException("Cannot remove project owner from membership", "remove_owner_forbidden")
        }

        val deleted = membershipRepository.deleteByProjectIdAndUserId(rootId, ObjectId(projectId), ObjectId(userId))
        if (deleted == 0L) {
            throw NotFoundException("Membership not found for user $userId in project $projectId")
        }

        // Update project.memberIds
        projectDoc.memberIds = projectDoc.memberIds.filter { it != ObjectId(userId) }
        projectDoc.updatedAt = Instant.now()
        projectRepository.update(projectDoc)
    }

    fun getProjectHistory(projectId: String, rootOrgId: String): List<AuditEntry> {
        val doc = projectRepository.findByIdAndRootOrg(ObjectId(projectId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Project not found: $projectId")
        return doc.history.map { OrganizationMapper.auditToDomain(it) }
    }
}
