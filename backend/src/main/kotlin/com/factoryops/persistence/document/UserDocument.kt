package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "users")
class UserDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var accountName: String
    lateinit var employeeNo: String
    var email: String? = null
    lateinit var displayName: String
    var roles: List<String> = emptyList()
    var orgManagerScopes: List<ObjectId> = emptyList()
    var groupIds: List<ObjectId> = emptyList()
    var primaryOrgPath: List<ObjectId> = emptyList()
    var hrSyncedAt: Instant? = null
    var active: Boolean = true
    /**
     * Consecutive failed login counter (M5.2 Block A / serves M5.3 S-016).
     * Reset to 0 on successful login; incremented on failed login.
     * Existing documents missing this field deserialize to 0 (backward compatible).
     */
    var failedLoginCount: Int = 0
    /**
     * Account lockout expiry timestamp (M5.2 Block A / serves M5.3 S-016).
     * null = not locked; non-null = locked until this instant.
     * Existing documents missing this field deserialize to null (backward compatible).
     */
    var lockedUntil: Instant? = null
    var schemaVersion: Int = 2
    var createdAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

@MongoEntity(collection = "user_credentials")
class UserCredentialsDocument : PanacheMongoEntity() {
    lateinit var userId: ObjectId
    lateinit var rootOrgId: ObjectId
    lateinit var passwordHash: String
    var algorithm: String = "BCRYPT"
    var updatedAt: Instant = Instant.now()
    var schemaVersion: Int = 1
}
