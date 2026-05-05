package com.factoryops.persistence.repository

import com.factoryops.persistence.document.ActionRequestDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.types.ObjectId

@ApplicationScoped
class ActionRequestRepository : PanacheMongoRepository<ActionRequestDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): ActionRequestDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<ActionRequestDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByStatus(rootOrgId: ObjectId, status: String): List<ActionRequestDocument> =
        find("rootOrgId = ?1 and status = ?2 and deletedAt is null", rootOrgId, status).list()

    fun findByTargetOrgId(rootOrgId: ObjectId, targetOrgId: ObjectId): List<ActionRequestDocument> =
        find("rootOrgId = ?1 and targetOrgId = ?2 and deletedAt is null", rootOrgId, targetOrgId).list()
}
