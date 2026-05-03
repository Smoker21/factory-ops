package com.factoryops.domain.membership

import java.time.Instant

/**
 * Project × User N:M 成員關係 aggregate root(ProjectMembership)。
 * 對應 spec §2 Aggregate 一覽 Membership。
 * 對應 MongoDB collection: memberships
 *
 * 為何獨立 collection:N:M 關係 + 獨立查詢需求。
 * Project 的 memberIds[] 為 denormalized 欄位,與此 collection 保持一致。
 */
data class Membership(
    val id: String? = null,
    val rootOrgId: String,
    val projectId: String,
    val userId: String,
    val role: MembershipRole = MembershipRole.MEMBER,
    val joinedAt: Instant = Instant.now(),
    val schemaVersion: Int = 1
)

/**
 * Project 成員角色枚舉。
 * 對應 openapi.yaml components/schemas/MembershipRole。
 */
enum class MembershipRole {
    MEMBER,
    LEAD,
    OBSERVER
}
