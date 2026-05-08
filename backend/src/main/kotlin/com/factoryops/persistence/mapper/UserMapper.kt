package com.factoryops.persistence.mapper

import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.user.User
import com.factoryops.domain.user.UserCredentials
import com.factoryops.persistence.document.UserCredentialsDocument
import com.factoryops.persistence.document.UserDocument
import org.bson.types.ObjectId

object UserMapper {

    fun toDomain(doc: UserDocument): User = User(
        id = doc.id!!.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        accountName = doc.accountName,
        employeeNo = doc.employeeNo,
        email = doc.email,
        displayName = doc.displayName,
        roles = doc.roles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() },
        orgManagerScopes = doc.orgManagerScopes.map { it.toHexString() },
        groupIds = doc.groupIds.map { it.toHexString() },
        primaryOrgPath = doc.primaryOrgPath.map { it.toHexString() },
        hrSyncedAt = doc.hrSyncedAt,
        active = doc.active,
        failedLoginCount = doc.failedLoginCount,
        lockedUntil = doc.lockedUntil,
        schemaVersion = doc.schemaVersion,
        createdAt = doc.createdAt,
        deletedAt = doc.deletedAt
    )

    fun toDocument(user: User): UserDocument {
        val doc = UserDocument()
        user.id?.let { doc.id = ObjectId(it) }
        doc.rootOrgId = ObjectId(user.rootOrgId)
        doc.accountName = user.accountName
        doc.employeeNo = user.employeeNo
        doc.email = user.email
        doc.displayName = user.displayName
        doc.roles = user.roles.map { it.name }
        doc.orgManagerScopes = user.orgManagerScopes.map { ObjectId(it) }
        doc.groupIds = user.groupIds.map { ObjectId(it) }
        doc.primaryOrgPath = user.primaryOrgPath.map { ObjectId(it) }
        doc.hrSyncedAt = user.hrSyncedAt
        doc.active = user.active
        doc.failedLoginCount = user.failedLoginCount
        doc.lockedUntil = user.lockedUntil
        doc.schemaVersion = user.schemaVersion
        doc.createdAt = user.createdAt
        doc.deletedAt = user.deletedAt
        return doc
    }

    fun credentialsToDomain(doc: UserCredentialsDocument): UserCredentials = UserCredentials(
        id = doc.id!!.toHexString(),
        userId = doc.userId.toHexString(),
        rootOrgId = doc.rootOrgId.toHexString(),
        passwordHash = doc.passwordHash,
        algorithm = doc.algorithm,
        updatedAt = doc.updatedAt,
        schemaVersion = doc.schemaVersion
    )

    fun credentialsToDocument(creds: UserCredentials): UserCredentialsDocument {
        val doc = UserCredentialsDocument()
        creds.id?.let { doc.id = ObjectId(it) }
        doc.userId = ObjectId(creds.userId)
        doc.rootOrgId = ObjectId(creds.rootOrgId)
        doc.passwordHash = creds.passwordHash
        doc.algorithm = creds.algorithm
        doc.updatedAt = creds.updatedAt
        doc.schemaVersion = creds.schemaVersion
        return doc
    }
}
