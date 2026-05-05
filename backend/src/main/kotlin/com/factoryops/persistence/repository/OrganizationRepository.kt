package com.factoryops.persistence.repository

import com.factoryops.persistence.document.OrganizationDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

@ApplicationScoped
class OrganizationRepository : PanacheMongoRepository<OrganizationDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): OrganizationDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findRoot(rootOrgId: ObjectId): OrganizationDocument? =
        find("_id = ?1 and deletedAt is null", rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<OrganizationDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByParentId(rootOrgId: ObjectId, parentId: ObjectId): List<OrganizationDocument> =
        find("rootOrgId = ?1 and parentId = ?2 and deletedAt is null", rootOrgId, parentId).list()

    fun findByCode(rootOrgId: ObjectId, code: String): OrganizationDocument? =
        find("rootOrgId = ?1 and code = ?2 and deletedAt is null", rootOrgId, code).firstResult()

    fun findRootByCode(code: String): OrganizationDocument? =
        find("code = ?1 and parentId is null and deletedAt is null", code).firstResult()

    fun findDescendants(nodeId: ObjectId): List<OrganizationDocument> =
        find(Document("ancestorIds", nodeId).append("deletedAt", null)).list()

    fun findLeafOrgs(rootOrgId: ObjectId): List<OrganizationDocument> =
        find("rootOrgId = ?1 and isLeaf = ?2 and deletedAt is null", rootOrgId, true).list()

    fun findByManagerId(managerId: ObjectId): List<OrganizationDocument> =
        find("managerId = ?1 and deletedAt is null", managerId).list()

    fun findRootOrgs(): List<OrganizationDocument> =
        find("parentId is null and deletedAt is null").list()

    fun countChildren(parentId: ObjectId): Long =
        count("parentId = ?1 and deletedAt is null", parentId)

    fun findWithCursor(rootOrgId: ObjectId, cursor: ObjectId?, limit: Int): List<OrganizationDocument> {
        val baseFilter = Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            Document("\$and", listOf(baseFilter, Document("_id", Document("\$gt", cursor))))
        } else baseFilter
        return find(filter).page(0, limit).list()
    }
}
