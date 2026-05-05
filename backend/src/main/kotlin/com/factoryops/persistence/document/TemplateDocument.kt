package com.factoryops.persistence.document

import io.quarkus.mongodb.panache.kotlin.PanacheMongoEntity
import io.quarkus.mongodb.panache.common.MongoEntity
import org.bson.types.ObjectId
import java.time.Instant

@MongoEntity(collection = "project_templates")
class ProjectTemplateDocument : PanacheMongoEntity() {
    var scope: String = "ORG"
    var rootOrgId: ObjectId? = null
    lateinit var name: String
    lateinit var code: String
    var descriptionMarkdown: String? = null
    var defaultMemberRoles: Map<String, Int> = emptyMap()
    var taskTemplateRefs: List<TaskTemplateRefDocument> = emptyList()
    var estimatedDurationDays: Int? = null
    var tags: List<String> = emptyList()
    var forkedFrom: ForkRefDocument? = null
    var version: Int = 1
    var active: Boolean = true
    lateinit var createdBy: ObjectId
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}

class TaskTemplateRefDocument {
    var taskTemplateScope: String = "ORG"
    var taskTemplateId: String = ""
    var version: Int = 1
    var sortOrder: Int = 1
    var estimatedHours: Double? = null
}

class ForkRefDocument {
    var sourceScope: String = "GLOBAL"
    var sourceId: String = ""
    var sourceVersion: Int = 1
    var forkedAt: Instant = Instant.now()
}

@MongoEntity(collection = "task_templates")
class TaskTemplateDocument : PanacheMongoEntity() {
    var scope: String = "ORG"
    var rootOrgId: ObjectId? = null
    lateinit var name: String
    lateinit var code: String
    lateinit var type: String
    var defaultPriority: String = "NORMAL"
    var defaultAttributes: Map<String, Any?> = emptyMap()
    var descriptionMarkdownTemplate: String? = null
    var defaultChecklist: List<Map<String, Any?>> = emptyList()
    var attachmentHints: List<String> = emptyList()
    var tags: List<String> = emptyList()
    var forkedFrom: ForkRefDocument? = null
    var version: Int = 1
    var active: Boolean = true
    lateinit var createdBy: ObjectId
    var history: List<AuditEntryDocument> = emptyList()
    var schemaVersion: Int = 1
    var createdAt: Instant = Instant.now()
    var updatedAt: Instant = Instant.now()
    var deletedAt: Instant? = null
}
