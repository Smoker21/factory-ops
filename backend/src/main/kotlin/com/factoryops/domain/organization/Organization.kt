package com.factoryops.domain.organization

import com.factoryops.domain.shared.AuditEntry
import java.time.Instant

/**
 * Organization aggregate root。
 * 對應 spec §4.1 Organization + domain-model.md §3 class diagram。
 * 對應 MongoDB collection: organizations
 *
 * 設計決策(ADR-0012):
 * - adjacency list(`parentId`)+ 物化路徑(`ancestorIds`)混合策略。
 * - `ancestorIds`:root 的直接祖先 ObjectId hex string 列表(root 自身為 `[]`)。
 *   寫入時由 application service 計算並維護(create / move 時在事務內更新自身及子孫)。
 * - `isLeaf`:衍生欄位,`type ∈ root.settings.leafTypes`。
 * - `depth`:衍生欄位,root = 0。
 *
 * 多租戶(ADR-0005):
 * - `rootOrgId`:租戶鍵;root 節點時等於自身 `id`。
 *
 * v1.3 變更(ADR-0010):
 * - 移除舊 `leaderId`(單值)。
 * - 新增 `managerId`(單值,nullable):ORG_MANAGER 角色的權威來源。
 * - 新增 `leaderIds[]`(0..N):ActionRequest dispatch ownerId 候選。
 *
 * 密碼:Organization 不存密碼;user credentials 在獨立 collection。
 *
 * Invariants:
 * - INV-12:parentId 鏈不可成環。
 * - INV-13:depth ≤ root.settings.orgMaxDepth。
 * - INV-26:parentId 必須與本節點同 rootOrgId。
 * - INV-30:code 在 rootOrgId 內 unique。
 * - INV-32:managerId 對應的 user 必須 active 且同 rootOrgId。
 * - INV-33:leaderIds 中每個 user 必須 active 且同 rootOrgId。
 * - INV-34:User.orgManagerScopes[] 為衍生 cache,由 reactor 維護。
 */
data class Organization(
    /** ObjectId hex string;null 表示 MongoDB 尚未 assign */
    val id: String? = null,
    /** 所屬樹的 root ObjectId hex string;root 節點時 = id */
    val rootOrgId: String,
    /** 直接父節點 ID;null = root */
    val parentId: String? = null,
    /** 多型 discriminator;FAB / DIVISION / DEPARTMENT / SECTION / 自訂 */
    val type: String,
    /** 節點名稱;Unicode;1-120 chars */
    val name: String,
    /** 同 rootOrgId 內唯一 code */
    val code: String,
    /** 唯一 manager userId(ObjectId hex string);v1.3 新欄位;nullable */
    val managerId: String? = null,
    /** 業務負責人 userId 列表(0..N);v1.3 新欄位;ActionRequest dispatch ownerId 候選 */
    val leaderIds: List<String> = emptyList(),
    /** 物化祖先 ID 陣列;root = [];寫入時維護(ADR-0012) */
    val ancestorIds: List<String> = emptyList(),
    /** 衍生欄位:是否為 leaf type(type ∈ root.settings.leafTypes) */
    val isLeaf: Boolean = false,
    /** 衍生欄位:從 root 的深度(root = 0) */
    val depth: Int = 0,
    /** IANA timezone;僅 root 有效(例 Asia/Taipei) */
    val timezone: String? = null,
    /** BCP-47 locale;僅 root 有效(例 zh-TW) */
    val locale: String? = null,
    /** Root 設定;僅 root 節點有效,其他節點為 null */
    val settings: OrgSettings? = null,
    /** append-only 稽核歷程;> 1000 筆時冷儲歸檔 */
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /** 軟刪除時間;null = 存活 */
    val deletedAt: Instant? = null
)
