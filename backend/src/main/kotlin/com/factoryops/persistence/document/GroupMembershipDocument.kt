package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "group_memberships")
class GroupMembershipDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var groupId: ObjectId
    lateinit var userId: ObjectId
    var role: String = "MEMBER"
    var joinedAt: Instant = Instant.now()
    var leftAt: Instant? = null
    lateinit var createdBy: ObjectId
    var schemaVersion: Int = 1
}
