package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "tasks")
class TaskDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var projectId: ObjectId
    lateinit var type: String
    lateinit var title: String
    var descriptionMarkdown: String? = null
    var status: String = "OPEN"
    var priority: String = "NORMAL"
    lateinit var ownerId: ObjectId
    var assignees: List<ObjectId> = emptyList()
    var attributes: Map<String, Any?> = emptyMap()
    var attachments: List<AttachmentRefDocument> = emptyList()
    var schedule: TimeRangeDocument? = null
    var completedAt: Instant? = null
    var originActionRequestId: ObjectId? = null
    var templateRef: TemplateRefDocument? = null
    var qaReviewPolicy: QaReviewPolicyDocument = QaReviewPolicyDocument()
    var qaReviews: List<QaReviewDocument> = emptyList()
    var comments: List<CommentDocument> = emptyList()
    var tags: List<String> = emptyList()
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    lateinit var createdBy: ObjectId
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class AttachmentRefDocument {
    var attachmentId: String = ""
    var alt: String? = null
    var role: String = "ATTACHMENT"
}

class QaReviewPolicyDocument {
    var dualSignRequired: Boolean = false
    var requiredReviewerRoles: List<String> = emptyList()
    var snapshotAt: Instant = Instant.now()
    var sourceGroupIds: List<ObjectId> = emptyList()
}

class QaReviewDocument {
    lateinit var reviewerId: ObjectId
    lateinit var reviewerRole: String
    var decision: String = "APPROVED"
    var reason: String? = null
    var at: Instant = Instant.now()
}

class CommentDocument {
    var id: String = ""
    lateinit var authorId: ObjectId
    var bodyMarkdown: String = ""
    var attachments: List<AttachmentRefDocument> = emptyList()
    var createdAt: Instant = Instant.now()
    var editedAt: Instant? = null
    var deletedAt: Instant? = null
}
