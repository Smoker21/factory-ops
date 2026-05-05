package com.factoryops.unit.mapper

import com.factoryops.domain.organization.OrgSettings
import com.factoryops.domain.organization.Organization
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.persistence.document.AuditEntryDocument
import com.factoryops.persistence.document.OrgSettingsDocument
import com.factoryops.persistence.document.OrganizationDocument
import com.factoryops.persistence.mapper.OrganizationMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for OrganizationMapper — Document ↔ Domain round-trips.
 */
class OrganizationMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val parentId = ObjectId()
        val managerId = ObjectId()
        val leaderId = ObjectId()
        val ancestorId = ObjectId()
        val actorId = ObjectId()
        val now = Instant.now()

        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.parentId = parentId
        doc.type = "DIVISION"
        doc.name = "製造部"
        doc.code = "manufacturing-div"
        doc.managerId = managerId
        doc.leaderIds = listOf(leaderId)
        doc.ancestorIds = listOf(ancestorId)
        doc.isLeaf = false
        doc.depth = 1
        doc.timezone = "Asia/Taipei"
        doc.locale = "zh-TW"
        doc.settings = OrgSettingsDocument().also { s ->
            s.orgMaxDepth = 5
            s.leafTypes = listOf("SECTION")
            s.attachmentMaxBytes = 52428800L
            s.extras = mapOf("custom" to "value")
        }
        val auditDoc = AuditEntryDocument()
        auditDoc.actorId = actorId
        auditDoc.action = "ORG_CREATED"
        auditDoc.at = now
        auditDoc.payload = mapOf("note" to "initial")
        doc.history = listOf(auditDoc)
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.updatedAt = now
        doc.deletedAt = null

        // When
        val domain = OrganizationMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(parentId.toHexString(), domain.parentId)
        assertEquals("DIVISION", domain.type)
        assertEquals("製造部", domain.name)
        assertEquals("manufacturing-div", domain.code)
        assertEquals(managerId.toHexString(), domain.managerId)
        assertEquals(listOf(leaderId.toHexString()), domain.leaderIds)
        assertEquals(listOf(ancestorId.toHexString()), domain.ancestorIds)
        assertEquals(false, domain.isLeaf)
        assertEquals(1, domain.depth)
        assertEquals("Asia/Taipei", domain.timezone)
        assertEquals("zh-TW", domain.locale)
        assertNotNull(domain.settings)
        assertEquals(5, domain.settings?.orgMaxDepth)
        assertEquals(listOf("SECTION"), domain.settings?.leafTypes)
        assertEquals(52428800L, domain.settings?.attachmentMaxBytes)
        assertEquals(1, domain.history.size)
        assertEquals("ORG_CREATED", domain.history[0].action)
        assertEquals(1, domain.schemaVersion)
        assertNull(domain.deletedAt)
    }

    @Test
    fun `toDomain maps minimal fields with null optionals`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val now = Instant.now()

        val doc = OrganizationDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.type = "FAB"
        doc.name = "Root Org"
        doc.code = "root-org"
        doc.depth = 0
        doc.createdAt = now
        doc.updatedAt = now

        // When
        val domain = OrganizationMapper.toDomain(doc)

        // Then
        assertNull(domain.parentId)
        assertNull(domain.managerId)
        assertTrue(domain.leaderIds.isEmpty())
        assertTrue(domain.ancestorIds.isEmpty())
        assertNull(domain.timezone)
        assertNull(domain.locale)
        assertNull(domain.settings)
        assertTrue(domain.history.isEmpty())
        assertNull(domain.deletedAt)
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val orgId = ObjectId().toHexString()
        val rootOrgId = ObjectId().toHexString()
        val parentId = ObjectId().toHexString()
        val managerId = ObjectId().toHexString()
        val leaderId = ObjectId().toHexString()
        val ancestorId = ObjectId().toHexString()
        val now = Instant.now()

        val org = Organization(
            id = orgId,
            rootOrgId = rootOrgId,
            parentId = parentId,
            type = "SECTION",
            name = "裝配課",
            code = "assembly-section",
            managerId = managerId,
            leaderIds = listOf(leaderId),
            ancestorIds = listOf(ancestorId),
            isLeaf = true,
            depth = 3,
            timezone = "Asia/Taipei",
            locale = "zh-TW",
            settings = OrgSettings(
                orgMaxDepth = 5,
                leafTypes = listOf("SECTION"),
                attachmentMaxBytes = 52428800L,
                extras = mapOf("key" to "val")
            ),
            history = listOf(AuditEntry(actorId = ObjectId().toHexString(), action = "ORG_CREATED", at = now)),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val doc = OrganizationMapper.toDocument(org)

        // Then
        assertEquals(ObjectId(orgId), doc.id)
        assertEquals(ObjectId(rootOrgId), doc.rootOrgId)
        assertEquals(ObjectId(parentId), doc.parentId)
        assertEquals("SECTION", doc.type)
        assertEquals("裝配課", doc.name)
        assertEquals("assembly-section", doc.code)
        assertEquals(ObjectId(managerId), doc.managerId)
        assertEquals(listOf(ObjectId(leaderId)), doc.leaderIds)
        assertEquals(listOf(ObjectId(ancestorId)), doc.ancestorIds)
        assertTrue(doc.isLeaf)
        assertEquals(3, doc.depth)
        assertEquals("Asia/Taipei", doc.timezone)
        assertEquals("zh-TW", doc.locale)
        assertNotNull(doc.settings)
        assertEquals(5, doc.settings?.orgMaxDepth)
        assertEquals(1, doc.history.size)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val rootOrgId = ObjectId().toHexString()
        val now = Instant.now()
        val org = Organization(
            id = null,
            rootOrgId = rootOrgId,
            type = "FAB",
            name = "New Org",
            code = "new-org",
            createdAt = now,
            updatedAt = now
        )

        // When
        val doc = OrganizationMapper.toDocument(org)

        // Then
        assertNull(doc.id)
    }

    // ─── Round-trip: toDomain(toDocument(org)) ────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves all fields`() {
        // Given
        val orgId = ObjectId().toHexString()
        val rootOrgId = ObjectId().toHexString()
        val now = Instant.now()
        val deletedAt = now.minusSeconds(3600)

        val original = Organization(
            id = orgId,
            rootOrgId = rootOrgId,
            parentId = null,
            type = "FAB",
            name = "Factory",
            code = "factory-001",
            managerId = null,
            leaderIds = emptyList(),
            ancestorIds = emptyList(),
            isLeaf = false,
            depth = 0,
            timezone = "Asia/Tokyo",
            locale = "ja-JP",
            settings = OrgSettings(orgMaxDepth = 4, leafTypes = listOf("LINE")),
            history = emptyList(),
            schemaVersion = 2,
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt
        )

        // When
        val roundTripped = OrganizationMapper.toDomain(OrganizationMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.rootOrgId, roundTripped.rootOrgId)
        assertEquals(original.type, roundTripped.type)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.code, roundTripped.code)
        assertEquals(original.isLeaf, roundTripped.isLeaf)
        assertEquals(original.depth, roundTripped.depth)
        assertEquals(original.timezone, roundTripped.timezone)
        assertEquals(original.locale, roundTripped.locale)
        assertEquals(original.settings?.orgMaxDepth, roundTripped.settings?.orgMaxDepth)
        assertEquals(original.settings?.leafTypes, roundTripped.settings?.leafTypes)
        assertEquals(original.schemaVersion, roundTripped.schemaVersion)
        assertEquals(original.deletedAt, roundTripped.deletedAt)
    }

    // ─── auditToDomain / auditToDocument ──────────────────────────────────────

    @Test
    fun `auditToDomain maps all fields correctly`() {
        // Given
        val actorId = ObjectId()
        val now = Instant.now()
        val doc = AuditEntryDocument()
        doc.actorId = actorId
        doc.action = "ORG_MANAGER_TRANSFERRED"
        doc.at = now
        doc.payload = mapOf("from" to "old-manager", "to" to "new-manager")

        // When
        val domain = OrganizationMapper.auditToDomain(doc)

        // Then
        assertEquals(actorId.toHexString(), domain.actorId)
        assertEquals("ORG_MANAGER_TRANSFERRED", domain.action)
        assertEquals(now, domain.at)
        assertEquals("old-manager", domain.payload["from"])
        assertEquals("new-manager", domain.payload["to"])
    }

    @Test
    fun `auditToDocument maps all fields correctly`() {
        // Given
        val actorId = ObjectId().toHexString()
        val now = Instant.now()
        val entry = AuditEntry(
            actorId = actorId,
            action = "ORG_DELETED",
            at = now,
            payload = mapOf("reason" to "decommissioned")
        )

        // When
        val doc = OrganizationMapper.auditToDocument(entry)

        // Then
        assertEquals(ObjectId(actorId), doc.actorId)
        assertEquals("ORG_DELETED", doc.action)
        assertEquals(now, doc.at)
        assertEquals("decommissioned", doc.payload["reason"])
    }

    @Test
    fun `audit round-trip preserves all fields`() {
        // Given
        val actorId = ObjectId().toHexString()
        val now = Instant.now()
        val original = AuditEntry(
            actorId = actorId,
            action = "SOME_ACTION",
            at = now,
            payload = mapOf("key1" to "val1", "key2" to 42)
        )

        // When
        val roundTripped = OrganizationMapper.auditToDomain(OrganizationMapper.auditToDocument(original))

        // Then
        assertEquals(original.actorId, roundTripped.actorId)
        assertEquals(original.action, roundTripped.action)
        assertEquals(original.at, roundTripped.at)
        assertEquals(original.payload, roundTripped.payload)
    }
}
