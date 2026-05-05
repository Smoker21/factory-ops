package com.factoryops.persistence.mapper

import com.factoryops.domain.membership.Membership
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.Project
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.domain.shared.TemplateRef
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.TemplateScope
import com.factoryops.persistence.document.MembershipDocument
import com.factoryops.persistence.document.ProjectDocument
import com.factoryops.persistence.document.TemplateRefDocument
import com.factoryops.persistence.document.TimeRangeDocument
import org.bson.types.ObjectId

object ProjectMapper {

    fun toDomain(doc: ProjectDocument): Project = Project(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        organizationId = doc.organizationId.toHexString(),
        name = doc.name,
        descriptionMarkdown = doc.descriptionMarkdown,
        status = runCatching { ProjectStatus.valueOf(doc.status) }.getOrDefault(ProjectStatus.DRAFT),
        ownerId = doc.ownerId.toHexString(),
        memberIds = doc.memberIds.map { it.toHexString() },
        groupIds = doc.groupIds.map { it.toHexString() },
        schedule = doc.schedule?.let { TimeRange(it.start, it.due) },
        tags = doc.tags,
        templateRef = doc.templateRef?.let { templateRefToDomain(it) },
        history = doc.history.map { OrganizationMapper.auditToDomain(it) },
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        updatedAt = doc.updatedAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(project: Project): ProjectDocument {
        val doc = ProjectDocument()
        project.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(project.rootOrgId)
        doc.organizationId = ObjectId(project.organizationId)
        doc.name = project.name
        doc.descriptionMarkdown = project.descriptionMarkdown
        doc.status = project.status.name
        doc.ownerId = ObjectId(project.ownerId)
        doc.memberIds = project.memberIds.map { ObjectId(it) }
        doc.groupIds = project.groupIds.map { ObjectId(it) }
        doc.schedule = project.schedule?.let { TimeRangeDocument().also { d -> d.start = it.start; d.due = it.due } }
        doc.tags = project.tags
        doc.templateRef = project.templateRef?.let { templateRefToDocument(it) }
        doc.history = project.history.map { OrganizationMapper.auditToDocument(it) }
        doc.schemaVersion = project.schemaVersion
        doc.createdAt = project.createdAt
        doc.updatedAt = project.updatedAt
        doc.deletedAt = project.deletedAt
        return doc
    }

    private fun templateRefToDomain(doc: TemplateRefDocument): TemplateRef = TemplateRef(
        templateType = doc.templateType,
        templateScope = runCatching { TemplateScope.valueOf(doc.templateScope) }.getOrDefault(TemplateScope.ORG),
        templateId = doc.templateId,
        templateVersion = doc.templateVersion,
        instantiatedAt = doc.instantiatedAt
    )

    private fun templateRefToDocument(ref: TemplateRef): TemplateRefDocument {
        val doc = TemplateRefDocument()
        doc.templateType = ref.templateType
        doc.templateScope = ref.templateScope.name
        doc.templateId = ref.templateId
        doc.templateVersion = ref.templateVersion
        doc.instantiatedAt = ref.instantiatedAt
        return doc
    }

    fun membershipToDomain(doc: MembershipDocument): Membership = Membership(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        projectId = doc.projectId.toHexString(),
        userId = doc.userId.toHexString(),
        role = runCatching { MembershipRole.valueOf(doc.role) }.getOrDefault(MembershipRole.MEMBER),
        joinedAt = doc.joinedAt,
        schemaVersion = doc.schemaVersion
    )

    fun membershipToDocument(membership: Membership): MembershipDocument {
        val doc = MembershipDocument()
        membership.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(membership.rootOrgId)
        doc.projectId = ObjectId(membership.projectId)
        doc.userId = ObjectId(membership.userId)
        doc.role = membership.role.name
        doc.joinedAt = membership.joinedAt
        doc.schemaVersion = membership.schemaVersion
        return doc
    }
}
