package com.factoryops.persistence.repository

import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.types.ObjectId

@ApplicationScoped
class UserRepository : PanacheMongoRepository<UserDocument> {

    fun findByIdAndNotDeleted(id: ObjectId, rootOrgId: ObjectId): UserDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByAccountName(rootOrgId: ObjectId, accountName: String): UserDocument? =
        find("rootOrgId = ?1 and accountName = ?2 and deletedAt is null", rootOrgId, accountName).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<UserDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findActiveByRootOrgId(rootOrgId: ObjectId): List<UserDocument> =
        find("rootOrgId = ?1 and active = ?2 and deletedAt is null", rootOrgId, true).list()

    fun searchByKeyword(rootOrgId: ObjectId, keyword: String): List<UserDocument> {
        val query = "rootOrgId = ?1 and deletedAt is null and (accountName like ?2 or displayName like ?2 or email like ?2 or employeeNo like ?2)"
        return find(query, rootOrgId, ".*$keyword.*").list()
    }

    fun findWithCursor(rootOrgId: ObjectId, cursor: org.bson.types.ObjectId?, limit: Int): List<UserDocument> {
        val baseFilter = org.bson.Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            org.bson.Document("\$and", listOf(baseFilter, org.bson.Document("_id", org.bson.Document("\$gt", cursor))))
        } else baseFilter
        return find(filter).page(0, limit).list()
    }
}

@ApplicationScoped
class UserCredentialsRepository : PanacheMongoRepository<UserCredentialsDocument> {

    fun findByUserId(userId: ObjectId): UserCredentialsDocument? =
        find("userId = ?1", userId).firstResult()

    fun findByRootOrgIdAndUserId(rootOrgId: ObjectId, userId: ObjectId): UserCredentialsDocument? =
        find("rootOrgId = ?1 and userId = ?2", rootOrgId, userId).firstResult()
}
