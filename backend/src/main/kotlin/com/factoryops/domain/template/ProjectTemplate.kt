package com.factoryops.domain.template

import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.enums.TemplateScope
import java.time.Instant

/**
 * ProjectTemplate aggregate root。
 * 對應 spec §4.4 ProjectTemplate(GLOBAL / ORG scope,ADR-0006)。
 * 對應 MongoDB collection: project_templates
 *
 * 版本管理:每次更新產生新 document(新 version);舊版本凍結。
 * GLOBAL 與 ORG 同一 collection;以 `scope` 欄位區分。
 * GLOBAL scope 時 rootOrgId = null。
 *
 * Invariant:
 * - (scope, rootOrgId, code, version) unique。
 * - GLOBAL Template 不可改為 ORG scope;反之亦然(只能 fork)。
 * - 版本一旦發佈不可修改;修改需建新 version。
 */
data class ProjectTemplate(
    val id: String? = null,
    /** GLOBAL 或 ORG */
    val scope: TemplateScope,
    /** GLOBAL 時為 null;ORG 時必填 */
    val rootOrgId: String? = null,
    val name: String,
    /** 同 (scope, rootOrgId) 內唯一 code */
    val code: String,
    val descriptionMarkdown: String? = null,
    /** 角色配置建議,例 `{ "ENGINEER": 1, "OPERATOR": 2 }` */
    val defaultMemberRoles: Map<String, Int> = emptyMap(),
    /** 內含的 TaskTemplate 引用列表(embed VO 陣列) */
    val taskTemplateRefs: List<TaskTemplateRef> = emptyList(),
    val estimatedDurationDays: Int? = null,
    val tags: List<String> = emptyList(),
    /** 若由 GLOBAL fork 而來則記錄;null = 非 fork */
    val forkedFrom: ForkRef? = null,
    /** 版本號;≥ 1;每次更新遞增 */
    val version: Int = 1,
    /** 是否為有效版本 */
    val active: Boolean = true,
    val createdBy: String,
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
