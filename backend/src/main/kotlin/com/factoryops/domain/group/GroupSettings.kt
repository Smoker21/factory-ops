package com.factoryops.domain.group

import com.factoryops.domain.shared.enums.Role

/**
 * Group-level 工作流客製設定 Value Object(v1.3 新增,ADR-0011)。
 * 對應 openapi.yaml components/schemas/GroupSettings + QaSettings。
 *
 * `settings` 在 Group 中為必填,預設值:
 * ```
 * GroupSettings(qa = QaSettings())
 * ```
 *
 * Task 建立時,server 從 Project.groupIds[].settings.qa 合併 snapshot 至 Task.qaReviewPolicy:
 * - dualSignRequired = OR(各 group.settings.qa.dualSignRequired)
 * - requiredReviewerRoles = ⋃(各 group.settings.qa.requiredReviewerRoles) 取聯集去重
 */
data class GroupSettings(
    /** QA 雙簽設定 */
    val qa: QaSettings = QaSettings(),
    /** 預留其他 Group-level 設定 */
    val extras: Map<String, Any?> = emptyMap()
)

/**
 * QA 雙簽設定子文件 Value Object。
 *
 * Invariant(INV-35):
 * - dualSignRequired = true 時 requiredReviewerRoles 不可為空。
 * - requiredReviewerRoles 只接受第一線角色:
 *   OPERATOR / SHIFT_LEAD / ENGINEER / QA / GROUP_ADMIN / GROUP_MANAGER。
 */
data class QaSettings(
    /** 是否啟用 QA 雙簽;預設 false */
    val dualSignRequired: Boolean = false,
    /**
     * 必要 reviewer 角色清單(AND 語意:每個列示角色都需各一筆 review,Q-23 預設)。
     * 僅允許第一線角色。
     */
    val requiredReviewerRoles: List<Role> = emptyList()
)
