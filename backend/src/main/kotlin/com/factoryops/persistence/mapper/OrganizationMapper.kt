package com.factoryops.persistence.mapper

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.persistence.document.AuditEntryDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.document.OrganizationDocument
import org.bson.types.ObjectId
import java.time.Instant

object OrganizationMapper {

    fun toDomain(doc: OrganizationDocument): Organization = Organization(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        parentId = doc.parentId?.toHexString(),
        type = doc.type,
        name = doc.name,
        code = doc.code,
        managerId = doc.managerId?.toHexString(),
        leaderIds = doc.leaderIds.map { it.toHexString() },
        ancestorIds = doc.ancestorIds.map { it.toHexString() },
        isLeaf = doc.isLeaf,
        depth = doc.depth,
        timezone = doc.timezone,
        locale = doc.locale,
        settings = doc.settings?.let { settingsToDomain(it) },
        history = doc.history.map { auditToDomain(it) },
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        updatedAt = doc.updatedAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(org: Organization): OrganizationDocument {
        val doc = OrganizationDocument()
        org.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(org.rootOrgId)
        doc.parentId = org.parentId?.let { ObjectId(it) }
        doc.type = org.type
        doc.name = org.name
        doc.code = org.code
        doc.managerId = org.managerId?.let { ObjectId(it) }
        doc.leaderIds = org.leaderIds.map { ObjectId(it) }
        doc.ancestorIds = org.ancestorIds.map { ObjectId(it) }
        doc.isLeaf = org.isLeaf
        doc.depth = org.depth
        doc.timezone = org.timezone
        doc.locale = org.locale
        doc.settings = org.settings?.let { settingsToDocument(it) }
        doc.history = org.history.map { auditToDocument(it) }
        doc.schemaVersion = org.schemaVersion
        doc.createdAt = org.createdAt
        doc.updatedAt = org.updatedAt
        doc.deletedAt = org.deletedAt
        return doc
    }

    private fun settingsToDomain(doc: OrgSettingsDocument): OrgSettings = OrgSettings(
        orgMaxDepth = doc.orgMaxDepth,
        leafTypes = doc.leafTypes,
        attachmentMaxBytes = doc.attachmentMaxBytes,
        extras = doc.extras
    )

    private fun settingsToDocument(settings: OrgSettings): OrgSettingsDocument {
        val doc = OrgSettingsDocument()
        doc.orgMaxDepth = settings.orgMaxDepth
        doc.leafTypes = settings.leafTypes
        doc.attachmentMaxBytes = settings.attachmentMaxBytes
        doc.extras = settings.extras
        return doc
    }

    fun auditToDomain(doc: AuditEntryDocument): AuditEntry = AuditEntry(
        actorId = doc.actorId.toHexString(),
        action = doc.action,
        at = doc.at,
        payload = doc.payload
    )

    fun auditToDocument(entry: AuditEntry): AuditEntryDocument {
        val doc = AuditEntryDocument()
        doc.actorId = ObjectId(entry.actorId)
        doc.action = entry.action
        doc.at = entry.at
        doc.payload = entry.payload
        return doc
    }
}
