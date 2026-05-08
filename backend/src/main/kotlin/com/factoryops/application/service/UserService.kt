package com.factoryops.application.service

import com.factoryops.application.auth.PasswordHasher
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.user.User
import com.factoryops.infrastructure.hr.HrClient
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.exception.BusinessRuleViolationException
import com.factoryops.interfaces.exception.ConflictException
import com.factoryops.interfaces.exception.ExternalServiceException
import com.factoryops.interfaces.exception.NotFoundException
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.persistence.mapper.UserMapper
import com.factoryops.persistence.repository.UserCredentialsRepository
import com.factoryops.persistence.repository.UserRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import mu.KotlinLogging
import org.bson.types.ObjectId
import java.security.SecureRandom
import java.time.Instant

private val logger = KotlinLogging.logger {}

// S-011: Temporary password charset excludes visually ambiguous characters (0/O/l/1)
private val TEMP_PASSWORD_CHARSET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789@#$%&*+".toCharArray()
private const val TEMP_PASSWORD_LENGTH = 16
private val secureRandom = SecureRandom()

// S-011: Password strength: >= 12 chars, >= 3 of: uppercase / lowercase / digit / symbol
private val UPPERCASE_REGEX = Regex("[A-Z]")
private val LOWERCASE_REGEX = Regex("[a-z]")
private val DIGIT_REGEX = Regex("[0-9]")
private val SYMBOL_REGEX = Regex("[^A-Za-z0-9]")

/**
 * Validates that a password meets the strength requirements.
 * Requirement: length >= 12, at least 3 of the 4 character classes (upper/lower/digit/symbol).
 */
internal fun validatePasswordStrength(password: String) {
    if (password.length < 12) {
        throw ValidationException(
            "New password must be at least 12 characters long",
            "password_too_short",
            "newPassword"
        )
    }
    val classCount = listOf(UPPERCASE_REGEX, LOWERCASE_REGEX, DIGIT_REGEX, SYMBOL_REGEX)
        .count { it.containsMatchIn(password) }
    if (classCount < 3) {
        throw ValidationException(
            "New password must contain at least 3 of: uppercase letters, lowercase letters, digits, symbols",
            "password_too_weak",
            "newPassword"
        )
    }
}

/**
 * Generates a 16-character cryptographically random temporary password.
 * Excludes visually ambiguous characters (0/O/l/1).
 * IMPORTANT: caller must return this value to the client and must NOT log it.
 */
internal fun generateTemporaryPassword(): String {
    return (1..TEMP_PASSWORD_LENGTH)
        .map { TEMP_PASSWORD_CHARSET[secureRandom.nextInt(TEMP_PASSWORD_CHARSET.size)] }
        .joinToString("")
}

/**
 * NOTE: @Transactional is applied at class level. MongoDB transactions require a replica set.
 * In dev/standalone mode Quarkus degrades to best-effort (no atomicity guarantee).
 */
@ApplicationScoped
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val credentialsRepository: UserCredentialsRepository,
    private val hrClient: HrClient,
    private val passwordHasher: PasswordHasher
) {

    fun listUsers(
        rootOrgId: String,
        q: String?,
        active: Boolean?,
        cursor: String? = null,
        limit: Int = 20
    ): Pair<List<User>, PageInfo> {
        val rootId = ObjectId(rootOrgId)
        val pageSize = limit.coerceIn(1, 100)
        val cursorId = cursor?.let { runCatching { ObjectId(it) }.getOrNull() }

        val docs = when {
            !q.isNullOrBlank() -> userRepository.searchByKeyword(rootId, q)
            active == true -> userRepository.findActiveByRootOrgId(rootId)
            else -> userRepository.findWithCursor(rootId, cursorId, pageSize + 1)
        }

        val hasMore = (q.isNullOrBlank() && active != true) && docs.size > pageSize
        val items = if (hasMore) docs.dropLast(1) else docs
        val nextCursor = if (hasMore) items.lastOrNull()?.id?.toHexString() else null

        return items.map { UserMapper.toDomain(it) } to PageInfo(nextCursor, hasMore)
    }

    fun getUser(userId: String, rootOrgId: String): User {
        val doc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw NotFoundException("User not found: $userId")
        return UserMapper.toDomain(doc)
    }

    fun getUserByAccountName(rootOrgId: String, accountName: String): User? {
        return userRepository.findByAccountName(ObjectId(rootOrgId), accountName)
            ?.let { UserMapper.toDomain(it) }
    }

    /**
     * Creates a user by syncing from HR (identified by accountName).
     *
     * S-011: Generates a cryptographically random 16-character temporary password internally.
     * The plain-text temporary password is returned in [CreateUserResult.temporaryPassword]
     * for one-time display to the caller. It is never logged.
     */
    fun createUser(
        rootOrgId: String,
        accountName: String,
        roles: List<Role>,
        actorId: String
    ): CreateUserResult {
        val rootId = ObjectId(rootOrgId)

        // Check for existing
        val existing = userRepository.findByAccountName(rootId, accountName)
        if (existing != null) {
            throw ConflictException("User with accountName '$accountName' already exists", "account_name_duplicate")
        }

        // Fetch from HR
        val hrEmployee = hrClient.findByAccountName(accountName)
            ?: throw ExternalServiceException("HR employee not found: $accountName", "hr_employee_not_found")

        if (!hrEmployee.active) {
            throw ValidationException("Employee is not active in HR system", "employee_not_active")
        }

        // S-011: Generate temporary password; never log it
        val temporaryPassword = generateTemporaryPassword()

        val now = Instant.now()
        val effectiveRoles = if (roles.isEmpty()) {
            hrEmployee.defaultRoles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
        } else roles

        val userDoc = com.factoryops.persistence.document.UserDocument().also { d ->
            d.rootOrgId = rootId
            d.accountName = hrEmployee.accountName
            d.employeeNo = hrEmployee.employeeNo
            d.email = hrEmployee.email
            d.displayName = hrEmployee.displayName
            d.roles = effectiveRoles.map { it.name }
            d.active = true
            d.hrSyncedAt = now
            d.createdAt = now
        }
        userRepository.persist(userDoc)

        // Store bcrypt hash (never store or log plain-text password)
        val credDoc = com.factoryops.persistence.document.UserCredentialsDocument().also { c ->
            c.userId = userDoc.id!!
            c.rootOrgId = rootId
            c.passwordHash = passwordHasher.hash(temporaryPassword)
            c.algorithm = "BCRYPT"
            c.updatedAt = now
        }
        credentialsRepository.persist(credDoc)

        logger.info { "Created user [${userDoc.id}] accountName=$accountName" }
        return CreateUserResult(user = UserMapper.toDomain(userDoc), temporaryPassword = temporaryPassword)
    }

    /** Result of createUser — carries the one-time temporary password for display to caller. */
    data class CreateUserResult(val user: User, val temporaryPassword: String)

    @Transactional
    fun updateUser(userId: String, rootOrgId: String, roles: List<Role>?, active: Boolean?, actorId: String): User {
        if (actorId == userId && roles != null) {
            throw BusinessRuleViolationException("Cannot modify your own roles", "cannot_modify_own_roles")
        }
        val doc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw NotFoundException("User not found: $userId")

        roles?.let { doc.roles = it.map { r -> r.name } }
        active?.let { doc.active = it }
        userRepository.update(doc)

        return UserMapper.toDomain(doc)
    }

    fun deleteUser(userId: String, rootOrgId: String, actorId: String) {
        val doc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw NotFoundException("User not found: $userId")

        doc.deletedAt = Instant.now()
        doc.active = false
        userRepository.update(doc)
        logger.info { "Soft-deleted user [$userId]" }
    }

    fun syncFromHr(userId: String, rootOrgId: String): User {
        val doc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw NotFoundException("User not found: $userId")

        val hrEmployee = hrClient.findByAccountName(doc.accountName)
            ?: throw ExternalServiceException("HR employee not found: ${doc.accountName}", "hr_employee_not_found")

        doc.displayName = hrEmployee.displayName
        doc.email = hrEmployee.email
        doc.active = hrEmployee.active
        doc.hrSyncedAt = Instant.now()
        userRepository.update(doc)

        return UserMapper.toDomain(doc)
    }

    fun getOrgManagerScopes(userId: String, rootOrgId: String): List<String> {
        val doc = userRepository.findByIdAndNotDeleted(ObjectId(userId), ObjectId(rootOrgId))
            ?: throw NotFoundException("User not found: $userId")
        return doc.orgManagerScopes.map { it.toHexString() }
    }
}
