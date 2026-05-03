package com.factoryops.domain.template

import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.TemplateScope
import java.time.Instant

/**
 * TaskTemplate aggregate root。
 * 對應 spec §4.5 TaskTemplate(GLOBAL / ORG scope,ADR-0006)。
 * 對應 MongoDB collection: task_templates
 *
 * 版本管理策略同 ProjectTemplate:每次更新產生新 document,舊版本凍結。
 */
data class TaskTemplate(
    val id: String? = null,
    val scope: TemplateScope,
    /** GLOBAL 時為 null;ORG 時必填 */
    val rootOrgId: String? = null,
    val name: String,
    val code: String,
    /** 對應 Task type discriminator,例 EQUIPMENT_INSPECTION */
    val type: String,
    val defaultPriority: Priority = Priority.NORMAL,
    /** 預填 Task attributes;實例化時 clone */
    val defaultAttributes: Map<String, Any?> = emptyMap(),
    /** Markdown 模板;可含 `{{equipmentId}}` 等 Handlebars 風格變數 */
    val descriptionMarkdownTemplate: String? = null,
    /** 預設檢查清單(embed VO 陣列) */
    val defaultChecklist: List<ChecklistItem> = emptyList(),
    /** 附件上傳提示說明 */
    val attachmentHints: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val forkedFrom: ForkRef? = null,
    val version: Int = 1,
    val active: Boolean = true,
    val createdBy: String,
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
