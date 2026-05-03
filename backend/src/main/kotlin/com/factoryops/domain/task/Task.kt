package com.factoryops.domain.task

import com.factoryops.domain.shared.AttachmentRef
import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.TemplateRef
import com.factoryops.domain.shared.TimeRange
import com.factoryops.domain.shared.enums.Priority
import java.time.Instant

/**
 * Task aggregate root(polymorphic by type)。
 * 對應 spec §4.7 Task(v1.3:加 qaReviewPolicy + qaReviews)。
 * 對應 MongoDB collection: tasks
 * 詳見 ADR-0001(polymorphic single-collection)。
 *
 * 多型設計:
 * - `type`:discriminator String(EQUIPMENT_INSPECTION / INCIDENT_RESPONSE / SHIFT_HANDOVER / …)
 * - `attributes`:Map<String, Any?>,type-specific;server 端以 JSON Schema 嚴格驗證。
 *
 * embed 決策:
 * - `comments[]`:embed,預估 ≤ 50 / Task;> 100 時門檻警告。
 * - `history[]`:embed,append-only;> 1000 筆歸檔。
 * - `qaReviewPolicy`:embed VO snapshot(不可變)。
 * - `qaReviews[]`:embed,預估 ≤ 5 / Task。
 * - `attachments[]`:僅存 AttachmentRef(ID + alt + role);實際檔案在 MinIO(ADR-0003)。
 *
 * Invariants:
 * - INV-1:ownerId ∈ assignees。
 * - INV-2:assignees distinct。
 * - INV-5:schedule.due > schedule.start(若皆設)。
 * - INV-6:dualSignRequired=true 時 IN_PROGRESS → DONE 直推被禁止。
 * - INV-8:CANCELLED 不可再轉其他狀態。
 * - INV-31:qaReviewPolicy 不可變(snapshot)。
 * - INV-36:(reviewerId, reviewerRole) 在同 Task 內不可重複。
 */
data class Task(
    val id: String? = null,
    val rootOrgId: String,
    val projectId: String,
    /**
     * 多型 discriminator;使用 TaskType 常數或自訂字串。
     * MongoDB 以 String 儲存;server 端 JSON Schema 驗證對應 attributes。
     */
    val type: String,
    val title: String,
    val descriptionMarkdown: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: Priority = Priority.NORMAL,
    /** 負責人 userId;必須 ∈ assignees */
    val ownerId: String,
    /** 指派人 userId 列表(distinct;含 ownerId) */
    val assignees: List<String> = emptyList(),
    /**
     * type-specific 屬性;以對應 JSON Schema 驗證。
     * 例如 EQUIPMENT_INSPECTION:{ "equipmentId": "...", "checklist": [...] }
     */
    val attributes: Map<String, Any?> = emptyMap(),
    /** 附件引用列表(AttachmentRef VO;實際檔案在 MinIO) */
    val attachments: List<AttachmentRef> = emptyList(),
    val schedule: TimeRange? = null,
    /** 進入 DONE 時設定 */
    val completedAt: Instant? = null,
    /** 由 ActionRequest convert 而來時設定(ref to action_requests) */
    val originActionRequestId: String? = null,
    /** 若由 TaskTemplate 實例化則記錄 snapshot */
    val templateRef: TemplateRef? = null,
    /**
     * QA 雙簽政策(v1.3 新增;必填)。
     * Task 建立時由 server snapshot from Project.groupIds[].settings.qa。
     * 不可變(INV-31)。
     */
    val qaReviewPolicy: QaReviewPolicy,
    /**
     * QA 審核記錄列表(v1.3 新增;append-only)。
     * 預估 ≤ 5 / Task;REJECTED 時預設清空(Q-21)。
     */
    val qaReviews: List<QaReview> = emptyList(),
    /**
     * 留言列表(embed)。
     * 預估 ≤ 50 / Task;超過 100 筆時應考慮拆分。
     */
    val comments: List<Comment> = emptyList(),
    val tags: List<String> = emptyList(),
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val createdBy: String,
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
