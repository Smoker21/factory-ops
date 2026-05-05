package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "groups")
class GroupDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var organizationId: ObjectId
    lateinit var type: String
    lateinit var name: String
    lateinit var code: String
    var leaderId: ObjectId? = null
    var attributes: Map<String, Any?> = emptyMap()
    var settings: GroupSettingsDocument = GroupSettingsDocument()
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class GroupSettingsDocument {
    var qa: QaSettingsDocument = QaSettingsDocument()
    var extras: Map<String, Any?> = emptyMap()
}

class QaSettingsDocument {
    var dualSignRequired: Boolean = false
    var requiredReviewerRoles: List<String> = emptyList()
}
