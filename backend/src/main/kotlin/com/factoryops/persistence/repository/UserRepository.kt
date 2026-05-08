package com.factoryops.persistence.repository

import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId
import java.util.regex.Pattern

private const val KEYWORD_MAX_LENGTH = 64

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

    /**
     * Searches users by keyword using prefix matching against accountName, displayName,
     * email, and employeeNo.
     *
     * S-013: Input is sanitised using Pattern.quote() to prevent ReDoS.
     * Wildcard characters '*' and '?' are rejected.
     * Keyword is capped at 64 characters.
     * Uses native MongoDB $regex with case-insensitive prefix matching (^<escaped>).
     */
    fun searchByKeyword(rootOrgId: ObjectId, keyword: String): List<UserDocument> {
        val trimmed = keyword.trim()
        require(trimmed.length <= KEYWORD_MAX_LENGTH) {
            "Search keyword must not exceed $KEYWORD_MAX_LENGTH characters"
        }
        require(!trimmed.contains('*') && !trimmed.contains('?')) {
            "Wildcard characters '*' and '?' are not allowed in search keywords"
        }

        // Escape all regex metacharacters; use ^ prefix anchor to avoid full-scan regex
        val escaped = Pattern.quote(trimmed)
        val prefixRegex = Document("\$regex", "^$escaped").append("\$options", "i")

        val filter = Document("rootOrgId", rootOrgId)
            .append("deletedAt", null)
            .append("\$or", listOf(
                Document("accountName", prefixRegex),
                Document("displayName", prefixRegex),
                Document("email", prefixRegex),
                Document("employeeNo", prefixRegex)
            ))

        return find(filter).list()
    }

    fun findWithCursor(rootOrgId: ObjectId, cursor: ObjectId?, limit: Int): List<UserDocument> {
        val baseFilter = Document("rootOrgId", rootOrgId).append("deletedAt", null)
        val filter = if (cursor != null) {
            Document("\$and", listOf(baseFilter, Document("_id", Document("\$gt", cursor))))
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
