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
    var schemaVersion: Int = 1
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
