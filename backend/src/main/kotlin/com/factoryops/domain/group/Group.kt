package com.factoryops.domain.group

import com.factoryops.domain.shared.AuditEntry
import java.time.Instant

/**
 * Group aggregate root。
 * 對應 spec §4.2 Group(平面 + polymorphic by type + settings.qa)。
 * 對應 MongoDB collection: groups
 *
 * 設計重點:
 * - 平面結構,無 parentId;屬唯一 leaf Organization。
 * - `type` 多型 discriminator(DEFAULT/LINE/TEAM/SHIFT/自訂)。
 * - `attributes`:type-specific 自由欄位,寬鬆 schema。
 * - `settings.qa`:v1.3 新增(ADR-0011);Task 建立時 snapshot 至 qaReviewPolicy。
 * - `leaderId`:Group 內組長(與 Organization.leaderIds 為不同概念)。
 *
 * Invariants(INV-14 / INV-15 / INV-16 / INV-22 / INV-23 / INV-35):
 * - organizationId 對應的 Org 必須是 leaf。
 * - leaderId 若非 null,該 user 必須有同 group 的 active GroupMembership。
 * - code 在同 rootOrgId 下 unique。
 * - settings.qa.dualSignRequired = true 時 requiredReviewerRoles 不可為空。
 */
data class Group(
    val id: String? = null,
    val rootOrgId: String,
    /** 必為 leaf Organization;ObjectId hex string */
    val organizationId: String,
    /** 多型 discriminator;DEFAULT/LINE/TEAM/SHIFT/自訂 */
    val type: String,
    val name: String,
    /** 同 rootOrgId 內唯一 code */
    val code: String,
    /** Group 內組長 userId;可 null;與 Organization.leaderIds 不同概念 */
    val leaderId: String? = null,
    /** type-specific 自由屬性(寬鬆 schema) */
    val attributes: Map<String, Any?> = emptyMap(),
    /** Group-level 工作流設定(必填,預設 { qa: { dualSignRequired: false, ... } }) */
    val settings: GroupSettings = GroupSettings(),
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
