package com.factoryops.persistence.repository

import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.ProjectDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts

@ApplicationScoped
class ProjectRepository : PanacheMongoRepository<ProjectDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): ProjectDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByOrganizationId(rootOrgId: ObjectId, organizationId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and organizationId = ?2 and deletedAt is null", rootOrgId, organizationId).list()

    fun findByGroupId(rootOrgId: ObjectId, groupId: ObjectId): List<ProjectDocument> =
        find(Document("rootOrgId", rootOrgId).append("groupIds", groupId).append("deletedAt", null)).list()

    fun findByStatus(rootOrgId: ObjectId, status: String): List<ProjectDocument> =
        find("rootOrgId = ?1 and status = ?2 and deletedAt is null", rootOrgId, status).list()

    fun findByOwnerId(rootOrgId: ObjectId, ownerId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and ownerId = ?2 and deletedAt is null", rootOrgId, ownerId).list()

    fun findByMemberId(rootOrgId: ObjectId, memberId: ObjectId): List<ProjectDocument> =
        find(Document("rootOrgId", rootOrgId).append("memberIds", memberId).append("deletedAt", null)).list()

    /**
     * C-014: Counts active (non-deleted) projects belonging to a given organization.
     * Used to block org deletion when active projects exist.
     */
    fun countActiveByOrg(organizationId: ObjectId): Long =
        count("organizationId = ?1 and deletedAt is null", organizationId)

    /**
     * Cursor-based pagination: returns up to [limit] documents with _id > [cursor] (if provided).
     * Caller should request limit+1 to detect hasMore.
     */
    fun findWithCursor(rootOrgId: ObjectId, cursor: ObjectId?, limit: Int): List<ProjectDocument> {
        val baseFilter = Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            Document("\$and", listOf(baseFilter, Document("_id", Document("\$gt", cursor))))
        } else baseFilter
        return find(filter).page(0, limit).list()
    }
}

@ApplicationScoped
class MembershipRepository : PanacheMongoRepository<MembershipDocument> {

    fun findByProjectId(rootOrgId: ObjectId, projectId: ObjectId): List<MembershipDocument> =
        find("rootOrgId = ?1 and projectId = ?2", rootOrgId, projectId).list()

    fun findByProjectIdAndUserId(rootOrgId: ObjectId, projectId: ObjectId, userId: ObjectId): MembershipDocument? =
        find("rootOrgId = ?1 and projectId = ?2 and userId = ?3", rootOrgId, projectId, userId).firstResult()

    fun deleteByProjectIdAndUserId(rootOrgId: ObjectId, projectId: ObjectId, userId: ObjectId): Long =
        delete("rootOrgId = ?1 and projectId = ?2 and userId = ?3", rootOrgId, projectId, userId)
}
