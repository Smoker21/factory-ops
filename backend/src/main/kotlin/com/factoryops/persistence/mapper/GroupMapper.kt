package com.factoryops.persistence.mapper

import com.factoryops.domain.group.Group
import com.factoryops.domain.group.GroupMembership
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.domain.group.GroupSettings
import com.factoryops.domain.group.QaSettings
import com.factoryops.domain.shared.enums.Role
import com.factoryops.persistence.document.GroupDocument
import com.factoryops.persistence.document.GroupMembershipDocument
import com.factoryops.persistence.document.GroupSettingsDocument
import com.factoryops.persistence.document.QaSettingsDocument
import org.bson.types.ObjectId

object GroupMapper {

    fun toDomain(doc: GroupDocument): Group = Group(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        organizationId = doc.organizationId.toHexString(),
        type = doc.type,
        name = doc.name,
        code = doc.code,
        leaderId = doc.leaderId?.toHexString(),
        attributes = doc.attributes,
        settings = settingsToDomain(doc.settings),
        history = doc.history.map { OrganizationMapper.auditToDomain(it) },
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        updatedAt = doc.updatedAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(group: Group): GroupDocument {
        val doc = GroupDocument()
        group.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(group.rootOrgId)
        doc.organizationId = ObjectId(group.organizationId)
        doc.type = group.type
        doc.name = group.name
        doc.code = group.code
        doc.leaderId = group.leaderId?.let { ObjectId(it) }
        doc.attributes = group.attributes
        doc.settings = settingsToDocument(group.settings)
        doc.history = group.history.map { OrganizationMapper.auditToDocument(it) }
        doc.schemaVersion = group.schemaVersion
        doc.createdAt = group.createdAt
        doc.updatedAt = group.updatedAt
        doc.deletedAt = group.deletedAt
        return doc
    }

    private fun settingsToDomain(doc: GroupSettingsDocument): GroupSettings = GroupSettings(
        qa = QaSettings(
            dualSignRequired = doc.qa.dualSignRequired,
            requiredReviewerRoles = doc.qa.requiredReviewerRoles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
        ),
        extras = doc.extras
    )

    private fun settingsToDocument(settings: GroupSettings): GroupSettingsDocument {
        val doc = GroupSettingsDocument()
        doc.qa = QaSettingsDocument().also {
            it.dualSignRequired = settings.qa.dualSignRequired
            it.requiredReviewerRoles = settings.qa.requiredReviewerRoles.map { r -> r.name }
        }
        doc.extras = settings.extras
        return doc
    }

    fun membershipToDomain(doc: GroupMembershipDocument): GroupMembership = GroupMembership(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        groupId = doc.groupId.toHexString(),
        userId = doc.userId.toHexString(),
        role = runCatching { GroupMembershipRole.valueOf(doc.role) }.getOrDefault(GroupMembershipRole.MEMBER),
        joinedAt = doc.joinedAt,
        leftAt = doc.leftAt,
        createdBy = doc.createdBy.toHexString(),
        schemaVersion = doc.schemaVersion
    )

    fun membershipToDocument(membership: GroupMembership): GroupMembershipDocument {
        val doc = GroupMembershipDocument()
        membership.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(membership.rootOrgId)
        doc.groupId = ObjectId(membership.groupId)
        doc.userId = ObjectId(membership.userId)
        doc.role = membership.role.name
        doc.joinedAt = membership.joinedAt
        doc.leftAt = membership.leftAt
        doc.createdBy = ObjectId(membership.createdBy)
        doc.schemaVersion = membership.schemaVersion
        return doc
    }
}
