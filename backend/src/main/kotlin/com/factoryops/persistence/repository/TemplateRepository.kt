package com.factoryops.persistence.repository

import com.factoryops.persistence.document.ProjectTemplateDocument
import com.factoryops.persistence.document.TaskTemplateDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.types.ObjectId

@ApplicationScoped
class ProjectTemplateRepository : PanacheMongoRepository<ProjectTemplateDocument> {

    fun findByIdNotDeleted(id: ObjectId): ProjectTemplateDocument? =
        find("_id = ?1 and deletedAt is null", id).firstResult()

    fun findGlobalTemplates(): List<ProjectTemplateDocument> =
        find("scope = 'GLOBAL' and active = true and deletedAt is null").list()

    fun findByRootOrgId(rootOrgId: ObjectId): List<ProjectTemplateDocument> =
        find("rootOrgId = ?1 and scope = 'ORG' and active = true and deletedAt is null", rootOrgId).list()

    fun findByRootOrgIdIncludeGlobal(rootOrgId: ObjectId): List<ProjectTemplateDocument> =
        find("(rootOrgId = ?1 or scope = 'GLOBAL') and active = true and deletedAt is null", rootOrgId).list()

    fun findByCode(scope: String, rootOrgId: ObjectId?, code: String): ProjectTemplateDocument? =
        if (rootOrgId != null) {
            find("scope = ?1 and rootOrgId = ?2 and code = ?3 and active = true and deletedAt is null", scope, rootOrgId, code).firstResult()
        } else {
            find("scope = ?1 and code = ?2 and active = true and deletedAt is null", scope, code).firstResult()
        }
}

@ApplicationScoped
class TaskTemplateRepository : PanacheMongoRepository<TaskTemplateDocument> {

    fun findByIdNotDeleted(id: ObjectId): TaskTemplateDocument? =
        find("_id = ?1 and deletedAt is null", id).firstResult()

    fun findGlobalTemplates(): List<TaskTemplateDocument> =
        find("scope = 'GLOBAL' and active = true and deletedAt is null").list()

    fun findByRootOrgId(rootOrgId: ObjectId): List<TaskTemplateDocument> =
        find("rootOrgId = ?1 and scope = 'ORG' and active = true and deletedAt is null", rootOrgId).list()

    fun findByRootOrgIdIncludeGlobal(rootOrgId: ObjectId): List<TaskTemplateDocument> =
        find("(rootOrgId = ?1 or scope = 'GLOBAL') and active = true and deletedAt is null", rootOrgId).list()
}
