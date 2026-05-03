package com.factoryops.domain.task

import com.factoryops.domain.shared.enums.Role
import java.time.Instant

/**
 * QA 雙簽審核政策 Value Object(snapshot,v1.3 新增)。
 * 對應 spec §4.7 QaReviewPolicy + openapi.yaml components/schemas/QaReviewPolicy。
 *
 * Task 建立時由 application service 從 Project.groupIds[].settings.qa 合併計算:
 * - dualSignRequired = OR(各 group.settings.qa.dualSignRequired)
 * - requiredReviewerRoles = ⋃(各 group.settings.qa.requiredReviewerRoles) 取聯集去重
 *
 * Invariant(INV-31):snapshot 一旦寫入後不可變;Group settings 後續變更不影響此 Task。
 */
data class QaReviewPolicy(
    /** 是否需要雙簽;OR 合併各 group 設定 */
    val dualSignRequired: Boolean,
    /** 必要 reviewer 角色清單(AND 語意;聯集去重) */
    val requiredReviewerRoles: List<Role>,
    /** snapshot 建立時間(UTC Instant) */
    val snapshotAt: Instant,
    /** 來源 Group ID 列表(稽核用) */
    val sourceGroupIds: List<String> = emptyList()
)
