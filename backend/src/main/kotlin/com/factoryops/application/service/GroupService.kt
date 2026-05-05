package com.factoryops.application.service

import com.factoryops.domain.group.Group
import com.factoryops.domain.group.GroupMembership
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.domain.group.GroupSettings
import com.factoryops.domain.group.QaSettings
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.QaSettingsDocument
import com.factoryops.persistence.mapper.GroupMapper
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.repository.GroupMembershipRepository
import com.factoryops.persistence.repository.GroupRepository
import com.factoryops.persistence.repository.OrganizationRepository
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
class GroupService(
    private val groupRepository: GroupRepository,
    private val membershipRepository: GroupMembershipRepository,
    private val orgRepository: OrganizationRepository,
    private val userRepository: UserRepository
) {

    fun listGroups(
        rootOrgId: String,
        organizationId: String?,
        type: String?,
        underOrgId: String?,
        cursor: String? = null,
        limit: Int = 20
    ): Pair<List<Group>, PageInfo> {
        val rootId = ObjectId(rootOrgId)
        val pageSize = limit.coerceIn(1, 100)
        val cursorId = cursor?.let { runCatching { ObjectId(it) }.getOrNull() }

        val docs = when {
            underOrgId != null -> {
                val nodeId = ObjectId(underOrgId)
                val node = orgRepository.findByIdAndRootOrg(nodeId, rootId)
                val descendants = orgRepository.findDescendants(nodeId)
                val leafOrgs = (listOfNotNull(node) + descendants).filter { it.isLeaf }
                groupRepository.findByOrganizationIds(rootId, leafOrgs.mapNotNull { it.id })
            }
            organizationId != null -> groupRepository.findByOrganizationId(rootId, ObjectId(organizationId))
            type != null -> groupRepository.findByRootOrgId(rootId)
            else -> groupRepository.findWithCursor(rootId, cursorId, pageSize + 1)
        }.filter { type == null || it.type == type }

        val hasMore = (organizationId == null && underOrgId == null && type == null) && docs.size > pageSize
        val items = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null

        return items.map { GroupMapper.toDomain(it) } to PageInfo(nextCursor, hasMore)
    }

    fun getGroup(groupId: String, rootOrgId: String): Group {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")
        return GroupMapper.toDomain(doc)
    }

    fun createGroup(
        rootOrgId: String,
        organizationId: String,
        type: String,
        name: String,
        code: String,
        leaderId: String?,
        attributes: Map<String, Any?>,
        actorId: String
    ): Group {
        val rootId = ObjectId(rootOrgId)

        val orgDoc = orgRepository.findByIdAndRootOrg(ObjectId(organizationId), rootId)
            ?: throw NotFoundException("Organization not found: $organizationId")
        if (!orgDoc.isLeaf) {
            throw ValidationException("Organization must be a leaf type to contain groups", "org_not_leaf")
        }

        val existing = groupRepository.findByCode(rootId, code)
        if (existing != null) {
            throw ConflictException("Group with code '$code' already exists", "code_duplicate")
        }

        val now = Instant.now()
        val doc = GroupDocument().also { d ->
            d.rootOrgId = rootId
            d.organizationId = ObjectId(organizationId)
            d.type = type
            d.name = name
            d.code = code
            d.leaderId = leaderId?.let { ObjectId(it) }
            d.attributes = attributes
            d.settings = GroupSettingsDocument()
            d.history = listOf(OrganizationMapper.auditToDocument(
                AuditEntry(actorId = actorId, action = "GROUP_CREATED", at = now)
            ))
            d.createdAt = now
            d.updatedAt = now
        }
        groupRepository.persist(doc)
        logger.info { "Created group [${doc.id}] name=$name" }
        return GroupMapper.toDomain(doc)
    }

    fun updateGroup(groupId: String, rootOrgId: String, name: String?, type: String?, attributes: Map<String, Any?>?, actorId: String): Group {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")

        val now = Instant.now()
        name?.let { doc.name = it }
        type?.let { doc.type = it }
        attributes?.let { doc.attributes = it }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "GROUP_UPDATED", at = now)
        )
        doc.updatedAt = now
        groupRepository.update(doc)
        return GroupMapper.toDomain(doc)
    }

    fun deleteGroup(groupId: String, rootOrgId: String, actorId: String) {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")

        val activeMembers = membershipRepository.findActiveByGroupId(ObjectId(rootOrgId), ObjectId(groupId))
        if (activeMembers.isNotEmpty()) {
            throw ConflictException("Group has ${activeMembers.size} active members. Remove members first.", "has_active_members")
        }

        doc.deletedAt = Instant.now()
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "GROUP_DELETED", at = Instant.now())
        )
        groupRepository.update(doc)
    }

    fun getGroupSettings(groupId: String, rootOrgId: String): GroupSettings {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")
        return GroupMapper.toDomain(doc).settings
    }

    fun updateGroupSettings(groupId: String, rootOrgId: String, qa: QaSettings?, extras: Map<String, Any?>?, actorId: String): GroupSettings {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")

        val now = Instant.now()
        val oldSettings = GroupMapper.toDomain(doc).settings

        qa?.let { newQa ->
            if (newQa.dualSignRequired && newQa.requiredReviewerRoles.isEmpty()) {
                throw ValidationException("requiredReviewerRoles cannot be empty when dualSignRequired is true", "qa_roles_required")
            }
            // INV-35: reviewer roles must be first-line operational roles only
            val allowedReviewerRoles = setOf("OPERATOR", "SHIFT_LEAD", "ENGINEER", "QA", "GROUP_ADMIN", "GROUP_MANAGER")
            val requestedRoles = newQa.requiredReviewerRoles.map { it.name }.toSet()
            if (!allowedReviewerRoles.containsAll(requestedRoles)) {
                val invalidRoles = requestedRoles - allowedReviewerRoles
                throw ValidationException("Invalid reviewer roles: $invalidRoles. Allowed: $allowedReviewerRoles", "invalid_reviewer_role")
            }
            doc.settings.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = newQa.dualSignRequired
                q.requiredReviewerRoles = newQa.requiredReviewerRoles.map { it.name }
            }
        }
        extras?.let { doc.settings.extras = it }

        val newSettings = GroupMapper.toDomain(doc).settings
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(
                actorId = actorId,
                action = "GROUP_SETTINGS_QA_UPDATED",
                at = now,
                payload = mapOf("before" to mapOf("dualSignRequired" to oldSettings.qa.dualSignRequired), "after" to mapOf("dualSignRequired" to newSettings.qa.dualSignRequired))
            )
        )
        doc.updatedAt = now
        groupRepository.update(doc)
        return newSettings
    }

    fun listMembers(groupId: String, rootOrgId: String, includeInactive: Boolean): List<GroupMembership> {
        val rootId = ObjectId(rootOrgId)
        val groupOId = ObjectId(groupId)
        return if (includeInactive) {
            membershipRepository.findByGroupIdIncludingInactive(rootId, groupOId)
        } else {
            membershipRepository.findActiveByGroupId(rootId, groupOId)
        }.map { GroupMapper.membershipToDomain(it) }
    }

    fun addMember(groupId: String, rootOrgId: String, userId: String, role: GroupMembershipRole, actorId: String): GroupMembership {
        val rootId = ObjectId(rootOrgId)

        groupRepository.findByIdAndRootOrg(ObjectId(groupId), rootId)
            ?: throw NotFoundException("Group not found: $groupId")

        val userDoc = userRepository.findByIdAndNotDeleted(ObjectId(userId), rootId)
            ?: throw NotFoundException("User not found: $userId")

        val existing = membershipRepository.findActiveByGroupIdAndUserId(rootId, ObjectId(groupId), ObjectId(userId))
        if (existing != null) {
            throw ConflictException("User is already an active member of this group", "already_member")
        }

        val now = Instant.now()
        val doc = GroupMembershipDocument().also { m ->
            m.rootOrgId = rootId
            m.groupId = ObjectId(groupId)
            m.userId = ObjectId(userId)
            m.role = role.name
            m.joinedAt = now
            m.createdBy = ObjectId(actorId)
        }
        membershipRepository.persist(doc)

        // Update User.groupIds cache
        val userDocToUpdate = userRepository.findById(ObjectId(userId))
        userDocToUpdate?.let { u ->
            val groupOid = ObjectId(groupId)
            if (!u.groupIds.contains(groupOid)) {
                u.groupIds = u.groupIds + groupOid
                userRepository.update(u)
            }
        }

        logger.info { "Added user [$userId] to group [$groupId] with role=${role.name}" }
        return GroupMapper.membershipToDomain(doc)
    }

    fun removeMember(groupId: String, rootOrgId: String, userId: String, actorId: String) {
        val rootId = ObjectId(rootOrgId)
        val groupDoc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), rootId)
            ?: throw NotFoundException("Group not found: $groupId")

        if (groupDoc.leaderId?.toHexString() == userId) {
            throw ConflictException("Cannot remove group leader. Transfer leadership first.", "remove_leader_forbidden")
        }

        val membership = membershipRepository.findActiveByGroupIdAndUserId(rootId, ObjectId(groupId), ObjectId(userId))
            ?: throw NotFoundException("Active membership not found for user $userId in group $groupId")

        membership.leftAt = Instant.now()
        membershipRepository.update(membership)

        // Update User.groupIds cache
        val userDoc = userRepository.findById(ObjectId(userId))
        userDoc?.let { u ->
            u.groupIds = u.groupIds.filter { it != ObjectId(groupId) }
            userRepository.update(u)
        }
    }

    fun transferLeader(groupId: String, rootOrgId: String, newLeaderId: String, actorId: String): Group {
        val rootId = ObjectId(rootOrgId)
        val groupDoc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), rootId)
            ?: throw NotFoundException("Group not found: $groupId")

        // New leader must be an active member
        val membership = membershipRepository.findActiveByGroupIdAndUserId(rootId, ObjectId(groupId), ObjectId(newLeaderId))
            ?: throw ConflictException("New leader must be an active member of the group", "not_a_member")

        val now = Instant.now()
        val oldLeaderId = groupDoc.leaderId?.toHexString()
        groupDoc.leaderId = ObjectId(newLeaderId)
        groupDoc.history = groupDoc.history + OrganizationMapper.auditToDocument(
            AuditEntry(
                actorId = actorId,
                action = "GROUP_LEADER_TRANSFERRED",
                at = now,
                payload = mapOf("from" to oldLeaderId, "to" to newLeaderId)
            )
        )
        groupDoc.updatedAt = now
        groupRepository.update(groupDoc)

        return GroupMapper.toDomain(groupDoc)
    }

    fun getGroupHistory(groupId: String, rootOrgId: String): List<AuditEntry> {
        val doc = groupRepository.findByIdAndRootOrg(ObjectId(groupId), ObjectId(rootOrgId))
            ?: throw NotFoundException("Group not found: $groupId")
        return doc.history.map { OrganizationMapper.auditToDomain(it) }
    }
}
