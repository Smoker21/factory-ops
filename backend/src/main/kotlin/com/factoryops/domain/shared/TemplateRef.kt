package com.factoryops.domain.shared

import com.factoryops.domain.shared.enums.TemplateScope
import java.time.Instant

/**
 * Template 實例化快照 Value Object。
 * 對應 spec §5 TemplateRef VO。
 * 由 Template 實例化時記錄,snapshot 後解耦(不形成執行期相依)。
 */
data class TemplateRef(
    /** PROJECT_TEMPLATE 或 TASK_TEMPLATE */
    val templateType: String,
    /** GLOBAL 或 ORG */
    val templateScope: TemplateScope,
    /** Template ID(ObjectId hex string) */
    val templateId: String,
    /** 實例化時使用的 version */
    val templateVersion: Int,
    /** 實例化時間(UTC Instant) */
    val instantiatedAt: Instant
)
