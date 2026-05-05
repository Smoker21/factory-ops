package com.factoryops.persistence.repository

import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.ProjectDocument
import io.quarkus.mongodb.panache.kotlin.PanacheMongoRepository
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.bson.types.ObjectId

@ApplicationScoped
class ProjectRepository : PanacheMongoRepository<ProjectDocument> {

    fun findByIdAndRootOrg(id: ObjectId, rootOrgId: ObjectId): ProjectDocument? =
        find("_id = ?1 and rootOrgId = ?2 and deletedAt is null", id, rootOrgId).firstResult()

    fun findByRootOrgId(rootOrgId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and deletedAt is null", rootOrgId).list()

    fun findByOrganizationId(rootOrgId: ObjectId, organizationId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and organizationId = ?2 and deletedAt is null", rootOrgId, organizationId).list()

    fun findByGroupId(rootOrgId: ObjectId, groupId: ObjectId): List<ProjectDocument> =
        find(Document("rootOrgId", rootOrgId).append("groupIds", groupId).append("deletedAt", null)).list()

    fun findByStatus(rootOrgId: ObjectId, status: String): List<ProjectDocument> =
        find("rootOrgId = ?1 and status = ?2 and deletedAt is null", rootOrgId, status).list()

    fun findByOwnerId(rootOrgId: ObjectId, ownerId: ObjectId): List<ProjectDocument> =
        find("rootOrgId = ?1 and ownerId = ?2 and deletedAt is null", rootOrgId, ownerId).list()

    fun findByMemberId(rootOrgId: ObjectId, memberId: ObjectId): List<ProjectDocument> =
        find(Document("rootOrgId", rootOrgId).append("memberIds", memberId).append("deletedAt", null)).list()
}

@ApplicationScoped
class MembershipRepository : PanacheMongoRepository<MembershipDocument> {

    fun findByProjectId(rootOrgId: ObjectId, projectId: ObjectId): List<MembershipDocument> =
        find("rootOrgId = ?1 and projectId = ?2", rootOrgId, projectId).list()

    fun findByProjectIdAndUserId(rootOrgId: ObjectId, projectId: ObjectId, userId: ObjectId): MembershipDocument? =
        find("rootOrgId = ?1 and projectId = ?2 and userId = ?3", rootOrgId, projectId, userId).firstResult()

    fun deleteByProjectIdAndUserId(rootOrgId: ObjectId, projectId: ObjectId, userId: ObjectId): Long =
        delete("rootOrgId = ?1 and projectId = ?2 and userId = ?3", rootOrgId, projectId, userId)
}
