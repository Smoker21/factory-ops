package com.factoryops.domain.actionrequest

import com.factoryops.domain.shared.AttachmentRef
import com.factoryops.domain.shared.AuditEntry
import java.time.Instant

/**
 * ActionRequest aggregate root(v1.3:Single-hop Direct Dispatch)。
 * 對應 spec §4.8 ActionRequest(v1.3)。
 * 對應 MongoDB collection: action_requests
 * 詳見 ADR-0008(v1.3 改寫)。
 *
 * v1.3 重要變更:
 * - `targetOrgId`:必為 leaf Organization(改名自 v1.2 assignedToOrgId)。
 * - 移除 `relayChain[]` 與 `RelayHop` VO。
 * - 移除 `RELAYED` 狀態。
 * - `originatingOrgId`:dispatch 發起者所掛節點(可任一層;若自課內提報則 = targetOrgId)。
 *
 * Invariants:
 * - INV-24:targetOrgId 必須是 leaf Organization。
 * - INV-25:targetOrgId 必須是 actor manager scope 節點的子孫。
 * - ownerId 規則(ADR-0010):
 *   - targetOrg.leaderIds.length == 0 → 409 target_org_no_leader
 *   - == 1 → 自動 ownerId = leaderIds[0]
 *   - >= 2 → 派工方指定(必須 ∈ leaderIds)
 */
data class ActionRequest(
    val id: String? = null,
    val rootOrgId: String,
    /** 自課內提報時必填;跨層 dispatch 起初可 null,triage 後關聯 Project */
    val projectId: String? = null,
    /** dispatch 發起者所掛 Org 節點;自課內提報時 = targetOrgId */
    val originatingOrgId: String,
    /** 承接 Org;必為 leaf Organization(INV-24) */
    val targetOrgId: String,
    val title: String,
    val descriptionMarkdown: String? = null,
    val severity: Severity,
    val status: ActionRequestStatus = ActionRequestStatus.SUBMITTED,
    /** 提報者 userId */
    val requesterId: String,
    /** 負責人 userId;ADR-0010 規則計算 */
    val ownerId: String? = null,
    /** triage 後設定;ref to tasks._id */
    val linkedTaskId: String? = null,
    val attachments: List<AttachmentRef> = emptyList(),
    val history: List<AuditEntry> = emptyList(),
    val submittedAt: Instant = Instant.now(),
    val resolvedAt: Instant? = null,
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)

/**
 * 嚴重程度 Value Object。
 * 對應 spec §5 Severity VO + openapi.yaml SeverityLevel。
 */
data class Severity(
    /** 嚴重程度等級 */
    val level: SeverityLevel,
    /** 原因說明 */
    val reason: String? = null
)

/**
 * 嚴重程度等級枚舉。
 * 對應 openapi.yaml components/schemas/SeverityLevel。
 */
enum class SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
