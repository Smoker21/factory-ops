package com.factoryops.domain.user

import com.factoryops.domain.shared.enums.Role
import java.time.Instant

/**
 * User aggregate root(HR 投影)。
 * 對應 spec §4.9 User + openapi.yaml components/schemas/User。
 * 對應 MongoDB collection: users
 *
 * 設計重點:
 * - HR 投影:profile 欄位(displayName/email/employeeNo)以 HR 為權威,由 HR sync 更新。
 * - 密碼不儲存於此;密碼 hash 在獨立 user_credentials collection(GDPR / 安全考量)。
 * - `roles[]`:系統管理;不含 ORG_MANAGER(衍生角色,v1.3)。
 * - `orgManagerScopes[]`:衍生 cache;權威來源是 Organization.managerId 反查;
 *    reactor 在 transfer-manager 後同步維護。
 * - `groupIds[]`:衍生 cache;active GroupMembership 反查。
 * - `primaryOrgPath[]`:衍生 cache;所屬 leaf Org 從 root 的路徑。
 *
 * Invariants:
 * - (rootOrgId, accountName) unique(partial:deletedAt = null)。
 * - (rootOrgId, employeeNo) unique(sparse)。
 */
data class User(
    val id: String? = null,
    val rootOrgId: String,
    /** HR 識別字;(rootOrgId, accountName) unique;最長 30 chars */
    val accountName: String,
    /** HR snapshot:工號 */
    val employeeNo: String,
    /** HR snapshot:電子郵件;可 null */
    val email: String? = null,
    /** HR snapshot:顯示名稱;Unicode */
    val displayName: String,
    /**
     * 系統角色清單;不含 ORG_MANAGER(衍生角色)。
     * 允許值:OPERATOR/SHIFT_LEAD/ENGINEER/QA/GROUP_ADMIN/GROUP_MANAGER/ORG_ADMIN/ADMIN。
     */
    val roles: List<Role> = emptyList(),
    /**
     * 衍生 cache:該 user 是哪些 Organization 節點的 managerId。
     * 權威來源是 Organization.managerId 反查;reactor 在 transfer-manager 後同步。
     * 授權判定可即時從 Organization 反查作為防呆。
     */
    val orgManagerScopes: List<String> = emptyList(),
    /** 衍生 cache:該 user 目前 active 所屬的 Group ID 列表 */
    val groupIds: List<String> = emptyList(),
    /** 衍生 cache:所屬 leaf Org 從 root 到 leaf 的 OrgId 路徑 */
    val primaryOrgPath: List<String> = emptyList(),
    /** 最近一次 HR 同步成功時間;可 null(尚未同步) */
    val hrSyncedAt: Instant? = null,
    /** HR 標記離職時為 false */
    val active: Boolean = true,
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
