package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "organizations")
class OrganizationDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    var parentId: ObjectId? = null
    lateinit var type: String
    lateinit var name: String
    lateinit var code: String
    var managerId: ObjectId? = null
    var leaderIds: List<ObjectId> = emptyList()
    var ancestorIds: List<ObjectId> = emptyList()
    var isLeaf: Boolean = false
    var depth: Int = 0
    var timezone: String? = null
    var locale: String? = null
    var settings: OrgSettingsDocument? = null
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class OrgSettingsDocument {
    var orgMaxDepth: Int = 5
    var leafTypes: List<String> = listOf("SECTION")
    var attachmentMaxBytes: Long = 52428800L
    var extras: Map<String, Any?> = emptyMap()
}

class AuditEntryDocument {
    lateinit var actorId: ObjectId
    lateinit var action: String
    var at: Instant = Instant.now()
    var payload: Map<String, Any?> = emptyMap()
}
