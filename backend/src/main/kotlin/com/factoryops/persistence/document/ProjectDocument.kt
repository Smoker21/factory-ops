package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "projects")
class ProjectDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var organizationId: ObjectId
    lateinit var name: String
    var descriptionMarkdown: String? = null
    var status: String = "DRAFT"
    lateinit var ownerId: ObjectId
    var memberIds: List<ObjectId> = emptyList()
    var groupIds: List<ObjectId> = emptyList()
    var schedule: TimeRangeDocument? = null
    var tags: List<String> = emptyList()
    var templateRef: TemplateRefDocument? = null
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class TimeRangeDocument {
    var start: Instant? = null
    var due: Instant? = null
}

class TemplateRefDocument {
    var templateType: String = "TASK_TEMPLATE"
    var templateScope: String = "ORG"
    var templateId: String = ""
    var templateVersion: Int = 1
    var instantiatedAt: Instant = Instant.now()
}

@MongoEntity(collection = "memberships")
class MembershipDocument : PanacheMongoEntity() {
    lateinit var rootOrgId: ObjectId
    lateinit var projectId: ObjectId
    lateinit var userId: ObjectId
    var role: String = "MEMBER"
    var joinedAt: Instant = Instant.now()
    var schemaVersion: Int = 1
}
