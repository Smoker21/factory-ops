package com.factoryops.unit

import com.factoryops.application.auth.PasswordHasher
import com.factoryops.application.service.UserService
import com.factoryops.domain.shared.enums.Role
import com.factoryops.infrastructure.hr.HrClient
import com.factoryops.infrastructure.hr.HrEmployee
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ExternalServiceException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

/**
 * Pure unit tests for UserService business rules.
 * Covers: user creation, HR sync, update, soft-delete.
 */
class UserServiceUnitTest {

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: UserCredentialsRepository
    private lateinit var hrClient: HrClient
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var userService: UserService

    private val rootId = ObjectId()
    private val userId = ObjectId()
    private val actorId = ObjectId()

    @BeforeEach
    fun setup() {
        userRepository = mock()
        credentialsRepository = mock()
        hrClient = mock()
        passwordHasher = mock()
        userService = UserService(userRepository, credentialsRepository, hrClient, passwordHasher)
    }

    // ─── Helper builders ────────────────────────────────────────────────────────

    private fun makeUserDoc(
        id: ObjectId = userId,
        accountName: String = "test.user",
        active: Boolean = true
    ): UserDocument {
        val doc = UserDocument()
        doc.id = id
        doc.rootOrgId = rootId
        doc.accountName = accountName
        doc.employeeNo = "EMP-001"
        doc.displayName = "Test User"
        doc.email = "test@factory.example.com"
        doc.roles = listOf("OPERATOR")
        doc.active = active
        doc.createdAt = Instant.now()
        return doc
    }

    private fun makeHrEmployee(
        accountName: String = "new.user",
        active: Boolean = true
    ): HrEmployee {
        return HrEmployee(
            accountName = accountName,
            employeeNo = "EMP-NEW",
            displayName = "New User",
            email = "newuser@factory.example.com",
            active = active,
            defaultRoles = listOf("OPERATOR")
        )
    }

    // ─── getUser ─────────────────────────────────────────────────────────────────

    @Test
    fun `should throw NotFoundException when user not found`() {
        // Given
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            userService.getUser(userId.toHexString(), rootId.toHexString())
        }
    }

    @Test
    fun `should return user domain object when found`() {
        // Given
        val userDoc = makeUserDoc()
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)

        // When
        val result = userService.getUser(userId.toHexString(), rootId.toHexString())

        // Then
        assertEquals(userId.toHexString(), result.id)
        assertEquals("test.user", result.accountName)
        assertEquals("EMP-001", result.employeeNo)
    }

    // ─── createUser ────────────────────────────────────────────────────────────

    @Test
    fun `should reject user creation when accountName already exists`() {
        // Given
        val existingDoc = makeUserDoc()
        whenever(userRepository.findByAccountName(eq(rootId), eq("test.user"))).thenReturn(existingDoc)

        // When / Then
        org.junit.jupiter.api.assertThrows<ConflictException> {
            userService.createUser(
                rootOrgId = rootId.toHexString(),
                accountName = "test.user",
                roles = emptyList(),
                defaultPassword = "TestPass@123",
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should reject user creation when HR employee not found`() {
        // Given
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)
        whenever(hrClient.findByAccountName(eq("nonexistent.user"))).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<ExternalServiceException> {
            userService.createUser(
                rootOrgId = rootId.toHexString(),
                accountName = "nonexistent.user",
                roles = emptyList(),
                defaultPassword = "TestPass@123",
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should reject user creation when HR employee is not active`() {
        // Given
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)
        val inactiveEmployee = makeHrEmployee(active = false)
        whenever(hrClient.findByAccountName(any())).thenReturn(inactiveEmployee)

        // When / Then
        org.junit.jupiter.api.assertThrows<ValidationException> {
            userService.createUser(
                rootOrgId = rootId.toHexString(),
                accountName = "inactive.user",
                roles = emptyList(),
                defaultPassword = "TestPass@123",
                actorId = actorId.toHexString()
            )
        }
    }

    @Test
    fun `should create user with provided roles when roles are specified`() {
        // Given
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)
        val hrEmployee = makeHrEmployee(active = true)
        whenever(hrClient.findByAccountName(any())).thenReturn(hrEmployee)
        whenever(passwordHasher.hash(any())).thenReturn("hashed-password")

        val capturedUser = argumentCaptor<UserDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<UserDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(userRepository).persist(capturedUser.capture())
        doNothing().whenever(credentialsRepository).persist(any<UserCredentialsDocument>())

        // When
        userService.createUser(
            rootOrgId = rootId.toHexString(),
            accountName = "new.user",
            roles = listOf(Role.ENGINEER, Role.QA),
            defaultPassword = "TestPass@123",
            actorId = actorId.toHexString()
        )

        // Then
        val persistedUser = capturedUser.firstValue
        assertEquals(listOf("ENGINEER", "QA"), persistedUser.roles)
    }

    @Test
    fun `should use HR default roles when no roles specified on create`() {
        // Given
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)
        val hrEmployee = makeHrEmployee(active = true)
        whenever(hrClient.findByAccountName(any())).thenReturn(hrEmployee)
        whenever(passwordHasher.hash(any())).thenReturn("hashed-password")

        val capturedUser = argumentCaptor<UserDocument>()
        doAnswer { invocation: InvocationOnMock ->
            val doc = invocation.getArgument<UserDocument>(0)
            doc.id = ObjectId()
            Unit
        }.whenever(userRepository).persist(capturedUser.capture())
        doNothing().whenever(credentialsRepository).persist(any<UserCredentialsDocument>())

        // When
        userService.createUser(
            rootOrgId = rootId.toHexString(),
            accountName = "new.user",
            roles = emptyList(),  // empty → use HR defaults
            defaultPassword = "TestPass@123",
            actorId = actorId.toHexString()
        )

        // Then: HR default role is OPERATOR
        val persistedUser = capturedUser.firstValue
        assertTrue(persistedUser.roles.contains("OPERATOR"))
    }

    // ─── updateUser ────────────────────────────────────────────────────────────

    @Test
    fun `should reject updating own roles`() {
        // Given
        val sameUserId = userId.toHexString()

        // When / Then
        org.junit.jupiter.api.assertThrows<BusinessRuleViolationException> {
            userService.updateUser(
                userId = sameUserId,
                rootOrgId = rootId.toHexString(),
                roles = listOf(Role.ADMIN),
                active = null,
                actorId = sameUserId  // same user trying to modify their own roles
            )
        }
    }

    @Test
    fun `should throw NotFoundException when updating non-existent user`() {
        // Given
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(null)
        val differentActorId = ObjectId().toHexString()

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            userService.updateUser(
                userId = userId.toHexString(),
                rootOrgId = rootId.toHexString(),
                roles = null,
                active = true,
                actorId = differentActorId
            )
        }
    }

    @Test
    fun `should update user roles and active flag when valid`() {
        // Given
        val userDoc = makeUserDoc(active = true)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        doNothing().whenever(userRepository).update(any<UserDocument>())
        val differentActorId = ObjectId().toHexString()

        // When
        val result = userService.updateUser(
            userId = userId.toHexString(),
            rootOrgId = rootId.toHexString(),
            roles = listOf(Role.SHIFT_LEAD),
            active = false,
            actorId = differentActorId
        )

        // Then
        assertEquals(listOf(Role.SHIFT_LEAD), result.roles)
        assertEquals(false, result.active)
    }

    // ─── deleteUser: soft-delete ────────────────────────────────────────────────

    @Test
    fun `should soft-delete user by setting deletedAt and deactivating`() {
        // Given
        val userDoc = makeUserDoc(active = true)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        val captured = argumentCaptor<UserDocument>()
        doNothing().whenever(userRepository).update(captured.capture())

        // When
        userService.deleteUser(userId.toHexString(), rootId.toHexString(), actorId.toHexString())

        // Then: update called (not delete), deletedAt set, active = false
        verify(userRepository, never()).delete(any<UserDocument>())
        val updated = captured.firstValue
        assertNotNull(updated.deletedAt, "deletedAt must be set on soft-delete")
        assertEquals(false, updated.active)
    }

    @Test
    fun `should throw NotFoundException when deleting non-existent user`() {
        // Given
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            userService.deleteUser(userId.toHexString(), rootId.toHexString(), actorId.toHexString())
        }
    }

    // ─── syncFromHr ─────────────────────────────────────────────────────────────

    @Test
    fun `should sync user fields from HR on syncFromHr`() {
        // Given
        val userDoc = makeUserDoc()
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        val updatedEmployee = HrEmployee(
            accountName = "test.user",
            employeeNo = "EMP-001",
            displayName = "Updated Name",
            email = "updated@factory.example.com",
            active = true
        )
        whenever(hrClient.findByAccountName(eq("test.user"))).thenReturn(updatedEmployee)
        doNothing().whenever(userRepository).update(any<UserDocument>())

        // When
        val result = userService.syncFromHr(userId.toHexString(), rootId.toHexString())

        // Then
        assertEquals("Updated Name", result.displayName)
        assertEquals("updated@factory.example.com", result.email)
        assertEquals(true, result.active)
    }

    @Test
    fun `should throw NotFoundException when syncing non-existent user`() {
        // Given
        whenever(userRepository.findByIdAndNotDeleted(any(), any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<NotFoundException> {
            userService.syncFromHr(userId.toHexString(), rootId.toHexString())
        }
    }

    @Test
    fun `should throw ExternalServiceException when HR employee not found during sync`() {
        // Given
        val userDoc = makeUserDoc()
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)
        whenever(hrClient.findByAccountName(any())).thenReturn(null)

        // When / Then
        org.junit.jupiter.api.assertThrows<ExternalServiceException> {
            userService.syncFromHr(userId.toHexString(), rootId.toHexString())
        }
    }

    // ─── getUserByAccountName ─────────────────────────────────────────────────

    @Test
    fun `should return null when user not found by account name`() {
        // Given
        whenever(userRepository.findByAccountName(any(), any())).thenReturn(null)

        // When
        val result = userService.getUserByAccountName(rootId.toHexString(), "nonexistent")

        // Then
        assertNull(result)
    }

    @Test
    fun `should return user when found by account name`() {
        // Given
        val userDoc = makeUserDoc(accountName = "found.user")
        whenever(userRepository.findByAccountName(eq(rootId), eq("found.user"))).thenReturn(userDoc)

        // When
        val result = userService.getUserByAccountName(rootId.toHexString(), "found.user")

        // Then
        assertNotNull(result)
        assertEquals("found.user", result?.accountName)
    }

    // ─── getOrgManagerScopes ─────────────────────────────────────────────────────

    @Test
    fun `should return orgManagerScopes for user`() {
        // Given
        val orgScopeId = ObjectId()
        val userDoc = makeUserDoc()
        userDoc.orgManagerScopes = listOf(orgScopeId)
        whenever(userRepository.findByIdAndNotDeleted(eq(userId), eq(rootId))).thenReturn(userDoc)

        // When
        val result = userService.getOrgManagerScopes(userId.toHexString(), rootId.toHexString())

        // Then
        assertEquals(listOf(orgScopeId.toHexString()), result)
    }
}
