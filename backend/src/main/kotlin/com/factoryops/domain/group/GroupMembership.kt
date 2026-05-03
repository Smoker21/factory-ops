package com.factoryops.domain.group

import java.time.Instant

/**
 * Group × User N:M 成員關係 aggregate root。
 * 對應 spec §4.3 GroupMembership(獨立 collection)。
 * 對應 MongoDB collection: group_memberships
 *
 * 為何獨立 collection(非 embed):
 * 1. N:M 關係:User 可屬多個 Group;Group 有多個 User。
 * 2. 保留歷程:`leftAt` 記錄離開時間。
 * 3. 雙向高頻查詢:「group 有哪些 active 成員」與「user 屬於哪些 group」都需要獨立索引。
 * 4. 獨立分頁:不適合 embed 在 Group 或 User。
 *
 * 注意:無 deletedAt;語意上以 leftAt 代替(null = active)。
 */
data class GroupMembership(
    val id: String? = null,
    val rootOrgId: String,
    val groupId: String,
    val userId: String,
    /** 成員角色 */
    val role: GroupMembershipRole = GroupMembershipRole.MEMBER,
    val joinedAt: Instant = Instant.now(),
    /** 離開時間;null = 仍為 active 成員 */
    val leftAt: Instant? = null,
    /** 建立此成員關係的操作者 userId */
    val createdBy: String,
    val schemaVersion: Int = 1
)

/**
 * Group 成員角色枚舉。
 * 對應 openapi.yaml components/schemas/GroupMembershipRole。
 */
enum class GroupMembershipRole {
    MEMBER,
    LEAD,
    OBSERVER
}
