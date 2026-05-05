package com.factoryops.application.service

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.mapper.OrganizationMapper
import com.factoryops.persistence.mapper.UserMapper
import com.factoryops.persistence.repository.OrganizationRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for organization tree management.
 * NOTE: @Transactional is applied on public methods. MongoDB transactions require a replica set.
 * In dev/standalone mode Quarkus degrades to best-effort (no atomicity guarantee).
 */
@ApplicationScoped
class OrganizationService(
    private val orgRepository: OrganizationRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun listOrgs(
        rootOrgId: String,
        parentId: String?,
        type: String?,
        leafOnly: Boolean,
        underOrgId: String?,
        cursor: String? = null,
        limit: Int = 50
    ): Pair<List<Organization>, PageInfo> {
        val rootId = ObjectId(rootOrgId)
        val pageSize = limit.coerceIn(1, 200)
        val cursorId = cursor?.let { runCatching { ObjectId(it) }.getOrNull() }

        val docs = when {
            underOrgId != null -> {
                val nodeId = ObjectId(underOrgId)
                val self = orgRepository.findByIdAndRootOrg(nodeId, rootId)
                val descendants = orgRepository.findDescendants(nodeId)
                listOfNotNull(self) + descendants
            }
            parentId != null -> orgRepository.findByParentId(rootId, ObjectId(parentId))
            type != null || leafOnly -> orgRepository.findByRootOrgId(rootId)
            else -> orgRepository.findWithCursor(rootId, cursorId, pageSize + 1)
        }.filter { type == null || it.type == type }
            .filter { !leafOnly || it.isLeaf }

        val hasMore = (parentId == null && underOrgId == null) && docs.size > pageSize
        val items = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null

        return items.map { OrganizationMapper.toDomain(it) } to PageInfo(nextCursor, hasMore)
    }

    @Transactional
    fun getOrg(id: String, rootOrgId: String): Organization {
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?: throw NotFoundException("Organization not found: $id")
        return OrganizationMapper.toDomain(doc)
    }

    @Transactional
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
        // Validate code uniqueness within root org
        val rootId = if (parentId == null) {
            null // root being created — code uniqueness across root orgs is not enforced (root codes can match)
        } else {
            val parent = orgRepository.findByIdAndRootOrg(
                ObjectId(parentId),
                actorRootOrgId?.let { ObjectId(it) } ?: throw ValidationException("actorRootOrgId required for non-root org creation")
            ) ?: throw NotFoundException("Parent organization not found: $parentId")
            parent.rootOrgId
        }

        if (rootId != null) {
            val existing = orgRepository.findByCode(rootId, code)
            if (existing != null) {
                throw ConflictException("Organization with code '$code' already exists", "code_duplicate")
            }
        }

        val parentDoc = parentId?.let {
            orgRepository.findByIdAndRootOrg(
                ObjectId(it),
                rootId ?: throw ValidationException("rootId must be resolved before lookup")
            ) ?: throw NotFoundException("Parent organization not found: $parentId")
        }

        val depth = (parentDoc?.depth ?: -1) + 1
        val ancestorIds = if (parentDoc == null) {
            emptyList()
        } else {
            parentDoc.ancestorIds.map { it.toHexString() } + parentDoc.id!!.toHexString()
        }

        // Pre-generate the ObjectId so we can set rootOrgId atomically in a single persist
        val newId = ObjectId()
        val resolvedRootOrgId = if (parentDoc == null) {
            // Root node: rootOrgId == self id
            newId
        } else {
            parentDoc.rootOrgId
        }

        // Compute isLeaf
        val rootSettings = if (parentDoc == null) settings else {
            val rootDoc = orgRepository.findRoot(resolvedRootOrgId)
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
            id = newId.toHexString(),
            rootOrgId = resolvedRootOrgId.toHexString(),
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

        logger.info { "Created organization [${doc.id}] name=$name type=$type" }
        return OrganizationMapper.toDomain(doc)
    }

    @Transactional
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
        val rootId = ObjectId(rootOrgId)
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), rootId)
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
                doc.settings = OrgSettingsDocument().also { d ->
                    d.orgMaxDepth = s.orgMaxDepth
                    d.leafTypes = s.leafTypes
                    d.attachmentMaxBytes = s.attachmentMaxBytes
                    d.extras = s.extras
                }
            }
        }

        if (newParentId != null && newParentId != doc.parentId?.toHexString()) {
            val newParent = orgRepository.findByIdAndRootOrg(ObjectId(newParentId), rootId)
                ?: throw NotFoundException("New parent not found: $newParentId")
            // Cycle check: newParent must not be in subtree of this node
            if (newParent.ancestorIds.any { it == doc.id } || newParent.id == doc.id) {
                throw ConflictException("Cannot move node to its own descendant (cycle)", "cycle_detected")
            }

            val oldAncestorIds = doc.ancestorIds.toList()
            val newAncestorIds = newParent.ancestorIds + newParent.id!!
            val newDepth = newParent.depth + 1

            doc.parentId = ObjectId(newParentId)
            doc.ancestorIds = newAncestorIds
            doc.depth = newDepth

            // Propagate ancestorIds update to all descendants (P0-14)
            propagateAncestorIdsToDescendants(doc.id!!, oldAncestorIds, newAncestorIds, newDepth)
        }

        doc.history = doc.history + historyEntry
        doc.updatedAt = now
        orgRepository.update(doc)

        return OrganizationMapper.toDomain(doc)
    }

    /**
     * Propagates ancestorIds and depth changes to all descendants of a moved node.
     * Uses bulk update to replace the old ancestor prefix with the new one for each descendant.
     */
    private fun propagateAncestorIdsToDescendants(
        movedNodeId: ObjectId,
        oldAncestorIds: List<ObjectId>,
        newAncestorIds: List<ObjectId>,
        newParentDepth: Int
    ) {
        val descendants = orgRepository.findDescendants(movedNodeId)
        for (descendant in descendants) {
            val oldPrefix = oldAncestorIds + movedNodeId
            val newPrefix = newAncestorIds + movedNodeId
            val oldAncestors = descendant.ancestorIds
            val newAncestors = if (oldAncestors.size >= oldPrefix.size) {
                newPrefix + oldAncestors.drop(oldPrefix.size)
            } else {
                newPrefix
            }
            val depthDelta = newAncestors.size - oldAncestors.size
            descendant.ancestorIds = newAncestors
            descendant.depth = descendant.depth + depthDelta
            descendant.updatedAt = Instant.now()
            orgRepository.update(descendant)
        }
    }

    @Transactional
    fun deleteOrg(id: String, rootOrgId: String, actorId: String) {
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
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

    @Transactional
    fun transferManager(id: String, rootOrgId: String, newManagerId: String?, actorId: String): Organization {
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
            ?: throw NotFoundException("Organization not found: $id")

        val now = Instant.now()
        val oldManagerId = doc.managerId?.toHexString()

        if (newManagerId != null) {
            val managerDoc = userRepository.findByIdAndNotDeleted(ObjectId(newManagerId), doc.rootOrgId)
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
    }

    @Transactional
    fun addLeader(id: String, rootOrgId: String, userId: String, actorId: String): Organization {
        val rootId = ObjectId(rootOrgId)
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), rootId)
            ?: throw NotFoundException("Organization not found: $id")

        val userDoc = userRepository.findByIdAndNotDeleted(ObjectId(userId), rootId)
            ?: throw NotFoundException("User not found: $userId")

        if (!userDoc.active) {
            throw ValidationException("User is not active", "user_not_active")
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

    @Transactional
    fun removeLeader(id: String, rootOrgId: String, userId: String, actorId: String) {
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), ObjectId(rootOrgId))
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

    @Transactional
    fun getLeaders(id: String, rootOrgId: String): List<com.factoryops.domain.user.User> {
        val rootId = ObjectId(rootOrgId)
        val doc = orgRepository.findByIdAndRootOrg(ObjectId(id), rootId)
            ?: throw NotFoundException("Organization not found: $id")

        return doc.leaderIds.mapNotNull { leaderId ->
            userRepository.findByIdAndNotDeleted(leaderId, rootId)?.let {
                UserMapper.toDomain(it)
            }
        }
    }
}
