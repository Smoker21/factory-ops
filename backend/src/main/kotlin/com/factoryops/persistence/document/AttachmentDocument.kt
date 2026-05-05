package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "attachments")
class AttachmentDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var mimeType: String
    var sizeBytes: Long = 0L
    lateinit var storageKey: String
    lateinit var uploaderId: ObjectId
    var ownerResourceType: String? = null
    var ownerResourceId: ObjectId? = null
    var status: String = "PENDING_UPLOAD"
    var metadata: AttachmentMetadataDocument? = null
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class AttachmentMetadataDocument {
    var width: Int? = null
    var height: Int? = null
    var durationMs: Long? = null
    var checksum: String? = null
}
