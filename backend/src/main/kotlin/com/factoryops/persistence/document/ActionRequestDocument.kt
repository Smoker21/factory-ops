package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "action_requests")
class ActionRequestDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    var projectId: ObjectId? = null
    lateinit var originatingOrgId: ObjectId
    lateinit var targetOrgId: ObjectId
    lateinit var title: String
    var descriptionMarkdown: String? = null
    var severity: SeverityDocument = SeverityDocument()
    var status: String = "SUBMITTED"
    lateinit var requesterId: ObjectId
    var ownerId: ObjectId? = null
    var linkedTaskId: ObjectId? = null
    var attachments: List<AttachmentRefDocument> = emptyList()
    var history: List<AuditEntryDocument> = emptyList()
    var submittedAt: Instant = Instant.now()
    var resolvedAt: Instant? = null
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class SeverityDocument {
    var level: String = "LOW"
    var reason: String? = null
}
