package com.factoryops.application.service

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.mapper.UserMapper
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.time.Instant

private val logger = KotlinLogging.logger {}

@ApplicationScoped
class OrganizationService(
    private val orgRepository: OrganizationRepository,
    private val userRepository: UserRepository
) {

    fun listOrgs(rootOrgId: String, parentId: String?, type: String?, leafOnly: Boolean, underOrgId: String?): List<Organization> {
        val rootId = ObjectId(rootOrgId)
        return when {
            underOrgId != null -> {
                val nodeId = ObjectId(underOrgId)
                val self = orgRepository.findByIdAndNotDeleted(nodeId)
                val descendants = orgRepository.findDescendants(nodeId)
                listOfNotNull(self) + descendants
            }
            parentId != null -> orgRepository.findByParentId(rootId, ObjectId(parentId))
            else -> orgRepository.findByRootOrgId(rootId)
        }
            .filter { type == null || it.type == type }
            .filter { !leafOnly || it.isLeaf }
            .map { OrganizationMapper.toDomain(it) }
    }

    fun getOrg(id: String, rootOrgId: String): Organization {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")
        return OrganizationMapper.toDomain(doc)
    }

    fun createOrg(
        type: String,
        name: String,
        code: String,
        parentId: String?,
        managerId: String?,
        leaderIds: List<String>,
        timezone: String?,
        locale: String?,
        settings: OrgSettings?,
        actorId: String,
        actorRootOrgId: String?
    ): Organization {
        // Validate code uniqueness
        val rootId = if (parentId == null) {
            null // will be assigned after creation
        } else {
            val parent = orgRepository.findByIdAndNotDeleted(ObjectId(parentId))
                ?: throw NotFoundException("Parent organization not found: $parentId")
            parent.rootOrgId
        }

        if (rootId != null) {
            val existing = orgRepository.findByCode(rootId, code)
            if (existing != null) {
                throw ConflictException("Organization with code '$code' already exists", "code_duplicate")
            }
        }

        val parentDoc = parentId?.let {
            orgRepository.findByIdAndNotDeleted(ObjectId(it))
                ?: throw NotFoundException("Parent organization not found: $parentId")
        }

        val depth = (parentDoc?.depth ?: -1) + 1
        val ancestorIds = if (parentDoc == null) {
            emptyList()
        } else {
            parentDoc.ancestorIds.map { it.toHexString() } + parentDoc.id!!.toHexString()
        }

        // Determine rootOrgId - for root nodes we'll use a temporary ObjectId and update after insert
        val tempRootOrgId = parentDoc?.rootOrgId?.toHexString() ?: ObjectId().toHexString()

        // Compute isLeaf
        val rootSettings = if (parentDoc == null) settings else {
            val rootDoc = orgRepository.findRoot(parentDoc.rootOrgId)
            rootDoc?.settings?.let {
                OrgSettings(it.orgMaxDepth, it.leafTypes, it.attachmentMaxBytes, it.extras)
            }
        }
        val leafTypes = rootSettings?.leafTypes ?: listOf("SECTION")
        val isLeaf = leafTypes.contains(type)

        // Validate depth
        val maxDepth = rootSettings?.orgMaxDepth ?: 5
        if (depth > maxDepth) {
            throw BusinessRuleViolationException("Organization tree depth $depth exceeds maximum $maxDepth", "depth_exceeded")
        }

        val now = Instant.now()
        val historyEntry = AuditEntry(actorId = actorId, action = "ORG_CREATED", at = now)

        val newOrg = Organization(
            rootOrgId = tempRootOrgId,
            parentId = parentId,
            type = type,
            name = name,
            code = code,
            managerId = managerId,
            leaderIds = leaderIds,
            ancestorIds = ancestorIds,
            isLeaf = isLeaf,
            depth = depth,
            timezone = timezone,
            locale = locale,
            settings = if (parentId == null) (settings ?: OrgSettings()) else null,
            history = listOf(historyEntry),
            createdAt = now,
            updatedAt = now
        )

        val doc = OrganizationMapper.toDocument(newOrg)
        orgRepository.persist(doc)

        // For root nodes, update rootOrgId to be self
        if (parentId == null) {
            doc.rootOrgId = doc.id!!
            orgRepository.update(doc)
        }

        logger.info { "Created organization [${doc.id}] name=$name type=$type" }
        return OrganizationMapper.toDomain(doc)
    }

    fun updateOrg(
        id: String,
        rootOrgId: String,
        name: String?,
        newParentId: String?,
        type: String?,
        timezone: String?,
        locale: String?,
        settings: OrgSettings?,
        actorId: String
    ): Organization {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        val now = Instant.now()
        val historyEntry = OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ORG_UPDATED", at = now)
        )

        name?.let { doc.name = it }
        type?.let { doc.type = it }
        timezone?.let { doc.timezone = it }
        locale?.let { doc.locale = it }
        settings?.let { s ->
            if (doc.parentId == null) {
                doc.settings = com.factoryops.persistence.document.OrgSettingsDocument().also { d ->
                    d.orgMaxDepth = s.orgMaxDepth
                    d.leafTypes = s.leafTypes
                    d.attachmentMaxBytes = s.attachmentMaxBytes
                    d.extras = s.extras
                }
            }
        }

        if (newParentId != null && newParentId != doc.parentId?.toHexString()) {
            val newParent = orgRepository.findByIdAndNotDeleted(ObjectId(newParentId))
                ?: throw NotFoundException("New parent not found: $newParentId")
            // Cycle check: newParent must not be in subtree of this node
            if (newParent.ancestorIds.any { it == doc.id } || newParent.id == doc.id) {
                throw ConflictException("Cannot move node to its own descendant (cycle)", "cycle_detected")
            }
            doc.parentId = ObjectId(newParentId)
            doc.ancestorIds = newParent.ancestorIds + newParent.id!!
            doc.depth = newParent.depth + 1
        }

        doc.history = doc.history + historyEntry
        doc.updatedAt = now
        orgRepository.update(doc)

        return OrganizationMapper.toDomain(doc)
    }

    fun deleteOrg(id: String, rootOrgId: String, actorId: String) {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        val childCount = orgRepository.countChildren(doc.id!!)
        if (childCount > 0) {
            throw ConflictException("Cannot delete organization with active children. Remove children first.", "has_children")
        }

        doc.deletedAt = Instant.now()
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ORG_DELETED", at = Instant.now())
        )
        orgRepository.update(doc)
        logger.info { "Soft-deleted organization [$id]" }
    }

    fun transferManager(id: String, rootOrgId: String, newManagerId: String?, actorId: String): Organization {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        val now = Instant.now()
        val oldManagerId = doc.managerId?.toHexString()

        if (newManagerId != null) {
            val managerDoc = userRepository.findById(ObjectId(newManagerId))
                ?: throw NotFoundException("User not found: $newManagerId")
            if (!managerDoc.active) {
                throw ValidationException("Manager user is not active", "user_not_active")
            }
        }

        doc.managerId = newManagerId?.let { ObjectId(it) }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(
                actorId = actorId,
                action = "ORG_MANAGER_TRANSFERRED",
                at = now,
                payload = mapOf("from" to oldManagerId, "to" to newManagerId)
            )
        )
        doc.updatedAt = now
        orgRepository.update(doc)

        // Update User.orgManagerScopes cache
        oldManagerId?.let { updateOrgManagerScopes(it, doc.id!!.toHexString(), remove = true) }
        newManagerId?.let { updateOrgManagerScopes(it, doc.id!!.toHexString(), remove = false) }

        return OrganizationMapper.toDomain(doc)
    }

    private fun updateOrgManagerScopes(userId: String, orgId: String, remove: Boolean) {
        try {
            val userDoc = userRepository.findById(ObjectId(userId)) ?: return
            val scopeId = ObjectId(orgId)
            if (remove) {
                userDoc.orgManagerScopes = userDoc.orgManagerScopes.filter { it != scopeId }
            } else {
                if (!userDoc.orgManagerScopes.contains(scopeId)) {
                    userDoc.orgManagerScopes = userDoc.orgManagerScopes + scopeId
                }
            }
            userRepository.update(userDoc)
        } catch (ex: Exception) {
            logger.warn { "Failed to update orgManagerScopes for user $userId: ${ex.message}" }
        }
    }

    fun addLeader(id: String, rootOrgId: String, userId: String, actorId: String): Organization {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        val userDoc = userRepository.findById(ObjectId(userId))
            ?: throw NotFoundException("User not found: $userId")

        if (!userDoc.active) {
            throw ValidationException("User is not active", "user_not_active")
        }
        if (userDoc.rootOrgId.toHexString() != doc.rootOrgId.toHexString()) {
            throw ValidationException("User does not belong to same root org", "different_root_org")
        }

        val leaderId = ObjectId(userId)
        if (doc.leaderIds.contains(leaderId)) {
            throw ConflictException("User is already a leader of this organization", "already_leader")
        }

        val now = Instant.now()
        doc.leaderIds = doc.leaderIds + leaderId
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ORG_LEADER_ADDED", at = now, payload = mapOf("userId" to userId))
        )
        doc.updatedAt = now
        orgRepository.update(doc)

        return OrganizationMapper.toDomain(doc)
    }

    fun removeLeader(id: String, rootOrgId: String, userId: String, actorId: String) {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        val leaderId = ObjectId(userId)
        if (!doc.leaderIds.contains(leaderId)) {
            throw NotFoundException("User is not a leader of this organization", "not_leader")
        }

        val now = Instant.now()
        doc.leaderIds = doc.leaderIds.filter { it != leaderId }
        doc.history = doc.history + OrganizationMapper.auditToDocument(
            AuditEntry(actorId = actorId, action = "ORG_LEADER_REMOVED", at = now, payload = mapOf("userId" to userId))
        )
        doc.updatedAt = now
        orgRepository.update(doc)
    }

    fun getLeaders(id: String, rootOrgId: String): List<com.factoryops.domain.user.User> {
        val doc = orgRepository.findByIdAndNotDeleted(ObjectId(id))
            ?: throw NotFoundException("Organization not found: $id")

        return doc.leaderIds.mapNotNull { leaderId ->
            userRepository.findById(leaderId)?.let {
                UserMapper.toDomain(it)
            }
        }
    }
}
