package com.factoryops.unit.mapper

import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.user.User
import com.factoryops.domain.user.UserCredentials
import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.mapper.UserMapper
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for UserMapper — Document ↔ Domain round-trips.
 */
class UserMapperTest {

    // ─── toDomain: full fields ─────────────────────────────────────────────────

    @Test
    fun `toDomain maps all fields when fully populated`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val orgScope = ObjectId()
        val groupId = ObjectId()
        val primaryOrgPathId = ObjectId()
        val now = Instant.now()
        val deletedAt = now.plusSeconds(3600)

        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "test.user"
        doc.employeeNo = "EMP-001"
        doc.email = "test@factory.example.com"
        doc.displayName = "Test User"
        doc.roles = listOf("OPERATOR", "ENGINEER")
        doc.orgManagerScopes = listOf(orgScope)
        doc.groupIds = listOf(groupId)
        doc.primaryOrgPath = listOf(primaryOrgPathId)
        doc.hrSyncedAt = now.minusSeconds(3600)
        doc.active = true
        doc.schemaVersion = 1
        doc.createdAt = now
        doc.deletedAt = deletedAt

        // When
        val domain = UserMapper.toDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals("test.user", domain.accountName)
        assertEquals("EMP-001", domain.employeeNo)
        assertEquals("test@factory.example.com", domain.email)
        assertEquals("Test User", domain.displayName)
        assertEquals(2, domain.roles.size)
        assertTrue(domain.roles.contains(Role.OPERATOR))
        assertTrue(domain.roles.contains(Role.ENGINEER))
        assertEquals(listOf(orgScope.toHexString()), domain.orgManagerScopes)
        assertEquals(listOf(groupId.toHexString()), domain.groupIds)
        assertEquals(listOf(primaryOrgPathId.toHexString()), domain.primaryOrgPath)
        assertEquals(true, domain.active)
        assertEquals(1, domain.schemaVersion)
        assertEquals(deletedAt, domain.deletedAt)
    }

    @Test
    fun `toDomain maps minimal fields with null optionals`() {
        // Given
        val id = ObjectId()
        val rootId = ObjectId()
        val now = Instant.now()

        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = "minimal.user"
        doc.employeeNo = "EMP-002"
        doc.displayName = "Minimal User"
        doc.email = null
        doc.hrSyncedAt = null
        doc.active = true
        doc.createdAt = now

        // When
        val domain = UserMapper.toDomain(doc)

        // Then
        assertNull(domain.email)
        assertNull(domain.hrSyncedAt)
        assertNull(domain.deletedAt)
        assertTrue(domain.roles.isEmpty())
        assertTrue(domain.orgManagerScopes.isEmpty())
        assertTrue(domain.groupIds.isEmpty())
        assertTrue(domain.primaryOrgPath.isEmpty())
    }

    @Test
    fun `toDomain skips unknown roles`() {
        // Given
        val doc = UserDocument()
        doc.id = ObjectId()
        doc.rootOrgId = ObjectId()
        doc.accountName = "unknown.role.user"
        doc.employeeNo = "EMP-003"
        doc.displayName = "Unknown Role User"
        doc.roles = listOf("QA", "UNKNOWN_ROLE", "ADMIN")
        doc.active = true
        doc.createdAt = Instant.now()

        // When
        val domain = UserMapper.toDomain(doc)

        // Then
        assertEquals(2, domain.roles.size)
        assertTrue(domain.roles.contains(Role.QA))
        assertTrue(domain.roles.contains(Role.ADMIN))
    }

    // ─── toDocument: domain → document ────────────────────────────────────────

    @Test
    fun `toDocument maps all fields when fully populated`() {
        // Given
        val userId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val orgScopeId = ObjectId().toHexString()
        val groupId = ObjectId().toHexString()
        val now = Instant.now()

        val user = User(
            id = userId,
            rootOrgId = rootId,
            accountName = "full.user",
            employeeNo = "EMP-004",
            email = "full@factory.example.com",
            displayName = "Full User",
            roles = listOf(Role.SHIFT_LEAD, Role.GROUP_MANAGER),
            orgManagerScopes = listOf(orgScopeId),
            groupIds = listOf(groupId),
            primaryOrgPath = listOf(rootId),
            hrSyncedAt = now.minusSeconds(1800),
            active = false,
            schemaVersion = 2,
            createdAt = now,
            deletedAt = now.plusSeconds(60)
        )

        // When
        val doc = UserMapper.toDocument(user)

        // Then
        assertEquals(ObjectId(userId), doc.id)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals("full.user", doc.accountName)
        assertEquals("EMP-004", doc.employeeNo)
        assertEquals("full@factory.example.com", doc.email)
        assertEquals("Full User", doc.displayName)
        assertEquals(listOf("SHIFT_LEAD", "GROUP_MANAGER"), doc.roles)
        assertEquals(listOf(ObjectId(orgScopeId)), doc.orgManagerScopes)
        assertEquals(listOf(ObjectId(groupId)), doc.groupIds)
        assertEquals(false, doc.active)
        assertEquals(2, doc.schemaVersion)
    }

    @Test
    fun `toDocument with null id does not set id`() {
        // Given
        val user = User(
            id = null,
            rootOrgId = ObjectId().toHexString(),
            accountName = "new.user",
            employeeNo = "EMP-005",
            displayName = "New User",
            active = true,
            createdAt = Instant.now()
        )

        // When
        val doc = UserMapper.toDocument(user)

        // Then
        assertNull(doc.id)
    }

    // ─── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `round-trip toDocument then toDomain preserves key fields`() {
        // Given
        val userId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val now = Instant.now()

        val original = User(
            id = userId,
            rootOrgId = rootId,
            accountName = "round.trip.user",
            employeeNo = "EMP-999",
            email = "round@factory.example.com",
            displayName = "Round Trip User",
            roles = listOf(Role.QA, Role.OPERATOR),
            active = true,
            schemaVersion = 1,
            createdAt = now
        )

        // When
        val roundTripped = UserMapper.toDomain(UserMapper.toDocument(original))

        // Then
        assertEquals(original.id, roundTripped.id)
        assertEquals(original.accountName, roundTripped.accountName)
        assertEquals(original.employeeNo, roundTripped.employeeNo)
        assertEquals(original.email, roundTripped.email)
        assertEquals(original.displayName, roundTripped.displayName)
        assertEquals(original.roles, roundTripped.roles)
        assertEquals(original.active, roundTripped.active)
        assertEquals(original.schemaVersion, roundTripped.schemaVersion)
    }

    // ─── credentialsToDomain ────────────────────────────────────────────────────

    @Test
    fun `credentialsToDomain maps all fields correctly`() {
        // Given
        val id = ObjectId()
        val userId = ObjectId()
        val rootId = ObjectId()
        val now = Instant.now()

        val doc = UserCredentialsDocument()
        doc.id = id
        doc.userId = userId
        doc.rootOrgId = rootId
        doc.passwordHash = "\$2a\$10\$hashedpassword"
        doc.algorithm = "BCRYPT"
        doc.updatedAt = now
        doc.schemaVersion = 1

        // When
        val domain = UserMapper.credentialsToDomain(doc)

        // Then
        assertEquals(id.toHexString(), domain.id)
        assertEquals(userId.toHexString(), domain.userId)
        assertEquals(rootId.toHexString(), domain.rootOrgId)
        assertEquals("\$2a\$10\$hashedpassword", domain.passwordHash)
        assertEquals("BCRYPT", domain.algorithm)
        assertEquals(now, domain.updatedAt)
        assertEquals(1, domain.schemaVersion)
    }

    // ─── credentialsToDocument ─────────────────────────────────────────────────

    @Test
    fun `credentialsToDocument maps all fields correctly`() {
        // Given
        val credId = ObjectId().toHexString()
        val userId = ObjectId().toHexString()
        val rootId = ObjectId().toHexString()
        val now = Instant.now()

        val creds = UserCredentials(
            id = credId,
            userId = userId,
            rootOrgId = rootId,
            passwordHash = "\$2a\$10\$hashed",
            algorithm = "BCRYPT",
            updatedAt = now,
            schemaVersion = 1
        )

        // When
        val doc = UserMapper.credentialsToDocument(creds)

        // Then
        assertEquals(ObjectId(credId), doc.id)
        assertEquals(ObjectId(userId), doc.userId)
        assertEquals(ObjectId(rootId), doc.rootOrgId)
        assertEquals("\$2a\$10\$hashed", doc.passwordHash)
        assertEquals("BCRYPT", doc.algorithm)
        assertEquals(now, doc.updatedAt)
    }

    @Test
    fun `credentialsToDocument with null id does not set id`() {
        // Given
        val creds = UserCredentials(
            id = null,
            userId = ObjectId().toHexString(),
            rootOrgId = ObjectId().toHexString(),
            passwordHash = "hash",
            algorithm = "BCRYPT",
            updatedAt = Instant.now(),
            schemaVersion = 1
        )

        // When
        val doc = UserMapper.credentialsToDocument(creds)

        // Then
        assertNull(doc.id)
    }
}
