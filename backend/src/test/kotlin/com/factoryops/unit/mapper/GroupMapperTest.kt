package com.factoryops.unit.mapper

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
import com.factoryops.persistence.mapper.GroupMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for GroupMapper — Document ↔ Domain round-trips.
 */
class GroupMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val orgId = ObjectId()
        val leaderId = ObjectId()
        val now = Instant.now()

        val doc = GroupDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.organizationId = orgId
        doc.type = "SHIFT"
        doc.name = "Night Shift"
        doc.code = "night-shift"
        doc.leaderId = leaderId
        doc.attributes = mapOf("shiftType" to "night", "capacity" to 10)
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = true
                q.requiredReviewerRoles = listOf("QA", "SHIFT_LEAD")
            }
            s.extras = mapOf("note" to "critical")
        }
        doc.history = emptyList()
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.updatedAt = now
        doc.deletedAt = null

        // When
        val domain = GroupMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(orgId.toHexString(), domain.organizationId)
        assertEquals("SHIFT", domain.type)
        assertEquals("Night Shift", domain.name)
        assertEquals("night-shift", domain.code)
        assertEquals(leaderId.toHexString(), domain.leaderId)
        assertEquals("night", domain.attributes["shiftType"])
        assertTrue(domain.settings.qa.dualSignRequired)
        assertEquals(2, domain.settings.qa.requiredReviewerRoles.size)
        assertTrue(domain.settings.qa.requiredReviewerRoles.contains(Role.QA))
        assertTrue(domain.settings.qa.requiredReviewerRoles.contains(Role.SHIFT_LEAD))
        assertEquals(mapOf("note" to "critical"), domain.settings.extras)
        assertNull(domain.deletedAt)
    }

    @Test
    fun `toDomain maps minimal fields with null leaderId`() {
        // Given
        val doc = GroupDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.organizationId = ObjectId()
        doc.type = "DEFAULT"
        doc.name = "Default Group"
        doc.code = "default-grp"
        doc.leaderId = null
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = GroupMapper.toDomain(doc)

        // Then
        assertNull(domain.leaderId)
        assertTrue(domain.attributes.isEmpty())
        assertTrue(domain.history.isEmpty())
    }

    @Test
    fun `toDomain skips unknown role in requiredReviewerRoles`() {
        // Given
        val doc = GroupDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.organizationId = ObjectId()
        doc.type = "SHIFT"
        doc.name = "Mixed Role Group"
        doc.code = "mixed-grp"
        doc.settings = GroupSettingsDocument().also { s ->
            s.qa = QaSettingsDocument().also { q ->
                q.dualSignRequired = true
                q.requiredReviewerRoles = listOf("QA", "UNKNOWN_ROLE", "SHIFT_LEAD")
            }
        }
        doc.createdAt = Instant.now()
        doc.updatedAt = Instant.now()

        // When
        val domain = GroupMapper.toDomain(doc)

        // Then
        assertEquals(2, domain.settings.qa.requiredReviewerRoles.size)
        assertTrue(domain.settings.qa.requiredReviewerRoles.contains(Role.QA))
        assertTrue(domain.settings.qa.requiredReviewerRoles.contains(Role.SHIFT_LEAD))
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val groupId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val orgId = ObjectId().toHexString()
        val leaderId = ObjectId().toHexString()
        val now = Instant.now()

        val group = Group(
            id = groupId,
            rootOrgId = rootId,
            organizationId = orgId,
            type = "LINE",
            name = "Assembly Line A",
            code = "asm-line-a",
            leaderId = leaderId,
            attributes = mapOf("lineSpeed" to 100),
            settings = GroupSettings(
                qa = QaSettings(
                    dualSignRequired = false,
                    requiredReviewerRoles = listOf(Role.ENGINEER)
                ),
                extras = mapOf("info" to "test")
            ),
            history = emptyList(),
            schemaVersion = 1,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )

        // When
        val doc = GroupMapper.toDocument(group)

        // Then
        assertEquals(ObjectId(groupId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(orgId), doc.organizationId)
        assertEquals("LINE", doc.type)
        assertEquals("Assembly Line A", doc.name)
        assertEquals("asm-line-a", doc.code)
        assertEquals(ObjectId(leaderId), doc.leaderId)
        assertEquals(100, doc.attributes["lineSpeed"])
        assertEquals(false, doc.settings.qa.dualSignRequired)
        assertEquals(listOf("ENGINEER"), doc.settings.qa.requiredReviewerRoles)
        assertEquals(mapOf("info" to "test"), doc.settings.extras)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val group = Group(
            id = null,
            rootOrgId = ObjectId().toHexString(),
            organizationId = ObjectId().toHexString(),
            type = "DEFAULT",
            name = "New Group",
            code = "new-grp",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // When
        val doc = GroupMapper.toDocument(group)

        // Then
        assertNull(doc.id)
    }

    @Test
    fun `toDocument with null leaderId maps null`() {
        // Given
        val group = Group(
            id = ObjectId().toHexString(),
            rootOrgId = ObjectId().toHexString(),
            organizationId = ObjectId().toHexString(),
            type = "DEFAULT",
            name = "No Leader Group",
            code = "no-leader",
            leaderId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        // When
        val doc = GroupMapper.toDocument(group)

        // Then
        assertNull(doc.leaderId)
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves key fields`() {
        // Given
        val groupId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val now = Instant.now()

        val original = Group(
            id = groupId,
            rootOrgId = rootId,
            organizationId = ObjectId().toHexString(),
            type = "TEAM",
            name = "QA Team",
            code = "qa-team",
            leaderId = null,
            settings = GroupSettings(
                qa = QaSettings(dualSignRequired = true, requiredReviewerRoles = listOf(Role.QA, Role.SHIFT_LEAD))
            ),
            schemaVersion = 2,
            createdAt = now,
            updatedAt = now
        )

        // When
        val roundTripped = GroupMapper.toDomain(GroupMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.type, roundTripped.type)
        assertEquals(original.name, roundTripped.name)
        assertEquals(original.code, roundTripped.code)
        assertEquals(original.settings.qa.dualSignRequired, roundTripped.settings.qa.dualSignRequired)
        assertEquals(original.settings.qa.requiredReviewerRoles.size, roundTripped.settings.qa.requiredReviewerRoles.size)
    }

    // ─── membershipToDomain ─────────────────────────────────────────────────────

    @Test
    fun `membershipToDomain maps all fields correctly`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val groupId = ObjectId()
        val userId = ObjectId()
        val createdById = ObjectId()
        val now = Instant.now()
        val leftAt = now.plusSeconds(3600)

        val doc = GroupMembershipDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.groupId = groupId
        doc.userId = userId
        doc.role = "LEAD"
        doc.joinedAt = now
        doc.leftAt = leftAt
        doc.createdBy = createdById
        doc.schemaVersion = 1

        // When
        val domain = GroupMapper.membershipToDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals(groupId.toHexString(), domain.groupId)
        assertEquals(userId.toHexString(), domain.userId)
        assertEquals(GroupMembershipRole.LEAD, domain.role)
        assertEquals(now, domain.joinedAt)
        assertEquals(leftAt, domain.leftAt)
        assertEquals(createdById.toHexString(), domain.createdBy)
    }

    @Test
    fun `membershipToDomain with unknown role defaults to MEMBER`() {
        // Given
        val doc = GroupMembershipDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.groupId = ObjectId()
        doc.userId = ObjectId()
        doc.role = "UNKNOWN_ROLE"
        doc.joinedAt = Instant.now()
        doc.createdBy = ObjectId()

        // When
        val domain = GroupMapper.membershipToDomain(doc)

        // Then
        assertEquals(GroupMembershipRole.MEMBER, domain.role)
    }

    @Test
    fun `membershipToDomain with null leftAt maps null`() {
        // Given
        val doc = GroupMembershipDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.groupId = ObjectId()
        doc.userId = ObjectId()
        doc.role = "MEMBER"
        doc.joinedAt = Instant.now()
        doc.leftAt = null
        doc.createdBy = ObjectId()

        // When
        val domain = GroupMapper.membershipToDomain(doc)

        // Then
        assertNull(domain.leftAt)
    }

    // ─── membershipToDocument ───────────────────────────────────────────────────

    @Test
    fun `membershipToDocument maps all fields correctly`() {
        // Given
        val memId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val groupId = ObjectId().toHexString()
        val userId = ObjectId().toHexString()
        val createdById = ObjectId().toHexString()
        val now = Instant.now()

        val membership = GroupMembership(
            id = memId,
            rootOrgId = rootId,
            groupId = groupId,
            userId = userId,
            role = GroupMembershipRole.OBSERVER,
            joinedAt = now,
            leftAt = null,
            createdBy = createdById,
            schemaVersion = 1
        )

        // When
        val doc = GroupMapper.membershipToDocument(membership)

        // Then
        assertEquals(ObjectId(memId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals(ObjectId(groupId), doc.groupId)
        assertEquals(ObjectId(userId), doc.userId)
        assertEquals("OBSERVER", doc.role)
        assertEquals(now, doc.joinedAt)
        assertNull(doc.leftAt)
        assertEquals(ObjectId(createdById), doc.createdBy)
    }
}
