package com.factoryops.persistence.repository

import com.factoryops.persistence.document.AttachmentDocument
import com.factoryops.persistence.document.EventOutboxDocument
import com.factoryops.persistence.document.WebhookDeadLetterDocument
import com.factoryops.persistence.document.WebhookDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId
import java.time.Instant

@ApplicationScoped
class AttachmentRepository : PanacheMongoRepository<AttachmentDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): AttachmentDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()
}

@ApplicationScoped
class WebhookRepository : PanacheMongoRepository<WebhookDocument> {

    fun findByRootOrgId(rootOrgId: ObjectId): List<WebhookDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findActiveByRootOrgIdAndEvent(rootOrgId: ObjectId, eventType: String): List<WebhookDocument> =
        find(Document("rootOrgId", rootOrgId).append("events", eventType).append("active", true).append("deletedAt", null)).list()
}

@ApplicationScoped
class WebhookDeadLetterRepository : PanacheMongoRepository<WebhookDeadLetterDocument> {

    fun findByRootOrgId(rootOrgId: ObjectId): List<WebhookDeadLetterDocument> =
        find("rootOrgId = ?1", rootOrgId).list()
}

@ApplicationScoped
class EventOutboxRepository : PanacheMongoRepository<EventOutboxDocument> {

    fun findPendingBatch(limit: Int): List<EventOutboxDocument> =
        find(Document("processedAt", null).append("scheduledAt", Document("\$lte", Instant.now())))
            .page(0, limit)
            .list()
}
