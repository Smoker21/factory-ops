package com.factoryops.persistence.repository

import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId

@ApplicationScoped
class GroupRepository : PanacheMongoRepository<GroupDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): GroupDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<GroupDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByOrganizationId(rootOrgId: ObjectId, organizationId: ObjectId): List<GroupDocument> =
        find("rootOrgId = ?1 and organizationId = ?2 and deletedAt is null", rootOrgId, organizationId).list()

    fun findByCode(rootOrgId: ObjectId, code: String): GroupDocument? =
        find("rootOrgId = ?1 and code = ?2 and deletedAt is null", rootOrgId, code).firstResult()

    fun findByOrganizationIds(rootOrgId: ObjectId, organizationIds: List<ObjectId>): List<GroupDocument> {
        if (organizationIds.isEmpty()) return emptyList()
        return find("rootOrgId = ?1 and organizationId in ?2 and deletedAt is null", rootOrgId, organizationIds).list()
    }

    /**
     * C-014: Counts groups (non-deleted) belonging to a given organization.
     * Used to block org deletion when groups exist.
     */
    fun countByOrg(organizationId: ObjectId): Long =
        count("organizationId = ?1 and deletedAt is null", organizationId)

    fun findWithCursor(rootOrgId: ObjectId, cursor: ObjectId?, limit: Int): List<GroupDocument> {
        val baseFilter = Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            Document("\$and", listOf(baseFilter, Document("_id", Document("\$gt", cursor))))
        } else baseFilter
        return find(filter).page(0, limit).list()
    }
}

@ApplicationScoped
class GroupMembershipRepository : PanacheMongoRepository<GroupMembershipDocument> {

    fun findActiveByGroupId(rootOrgId: ObjectId, groupId: ObjectId): List<GroupMembershipDocument> =
        find("rootOrgId = ?1 and groupId = ?2 and leftAt is null", rootOrgId, groupId).list()

    fun findByGroupIdIncludingInactive(rootOrgId: ObjectId, groupId: ObjectId): List<GroupMembershipDocument> =
        find("rootOrgId = ?1 and groupId = ?2", rootOrgId, groupId).list()

    fun findActiveByUserId(rootOrgId: ObjectId, userId: ObjectId): List<GroupMembershipDocument> =
        find("rootOrgId = ?1 and userId = ?2 and leftAt is null", rootOrgId, userId).list()

    fun findActiveByGroupIdAndUserId(rootOrgId: ObjectId, groupId: ObjectId, userId: ObjectId): GroupMembershipDocument? =
        find("rootOrgId = ?1 and groupId = ?2 and userId = ?3 and leftAt is null", rootOrgId, groupId, userId).firstResult()
}
