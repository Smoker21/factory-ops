package com.factoryops.persistence.repository

import com.factoryops.persistence.document.TaskDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId

@ApplicationScoped
class TaskRepository : PanacheMongoRepository<TaskDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): TaskDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<TaskDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByProjectId(rootOrgId: ObjectId, projectId: ObjectId): List<TaskDocument> =
        find("rootOrgId = ?1 and projectId = ?2 and deletedAt is null", rootOrgId, projectId).list()

    fun findByStatus(rootOrgId: ObjectId, status: String): List<TaskDocument> =
        find("rootOrgId = ?1 and status = ?2 and deletedAt is null", rootOrgId, status).list()

    fun findByOwnerId(rootOrgId: ObjectId, ownerId: ObjectId): List<TaskDocument> =
        find("rootOrgId = ?1 and ownerId = ?2 and deletedAt is null", rootOrgId, ownerId).list()

    fun findByAssigneeId(rootOrgId: ObjectId, assigneeId: ObjectId): List<TaskDocument> =
        find(Document("rootOrgId", rootOrgId).append("assignees", assigneeId).append("deletedAt", null)).list()

    fun countActiveByProjectId(rootOrgId: ObjectId, projectId: ObjectId): Long =
        count("rootOrgId = ?1 and projectId = ?2 and deletedAt is null and status not in ['DONE', 'CANCELLED']", rootOrgId, projectId)

    fun findWithCursor(rootOrgId: ObjectId, cursor: ObjectId?, limit: Int): List<TaskDocument> {
        val baseFilter = Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            Document("\$and", listOf(baseFilter, Document("_id", Document("\$gt", cursor))))
        } else baseFilter
        return find(filter).page(0, limit).list()
    }
}
