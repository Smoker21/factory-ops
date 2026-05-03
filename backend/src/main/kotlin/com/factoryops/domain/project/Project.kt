package com.factoryops.domain.project

import com.factoryops.domain.shared.AuditEntry
import com.factoryops.domain.shared.TemplateRef
import com.factoryops.domain.shared.TimeRange
import java.time.Instant

/**
 * Project aggregate root。
 * 對應 spec §4.6 Project(v1.3:groupIds 必屬同 leaf Org)。
 * 對應 MongoDB collection: projects
 *
 * 設計重點:
 * - 屬唯一 leaf Organization(INV-21)。
 * - groupIds[]:所有 group 必須屬於 Project.organizationId 同一個 leaf Org(INV-19,Q-13)。
 * - memberIds[]:含 ownerId;Project 的 memberIds 為 denormalized 欄位,
 *   與 memberships collection 保持一致。
 * - templateRef:clone 來源 snapshot(不可變)。
 *
 * Invariants:
 * - ownerId ∈ memberIds。
 * - schedule.due > schedule.start(若兩者皆設)。
 * - COMPLETED/CANCELLED 後不允許新增 Task / ActionRequest。
 * - groupIds[] 中每個 group 必須 Group.organizationId == Project.organizationId。
 */
data class Project(
    val id: String? = null,
    val rootOrgId: String,
    /** 必為 leaf Organization;ObjectId hex string */
    val organizationId: String,
    val name: String,
    val descriptionMarkdown: String? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val ownerId: String,
    /** distinct userId 列表;含 ownerId */
    val memberIds: List<String> = emptyList(),
    /**
     * Project 所屬 Group ID 列表;至少 1 個;
     * 每個必須與 Project.organizationId 對應的同一個 leaf Org。
     */
    val groupIds: List<String> = emptyList(),
    val schedule: TimeRange? = null,
    val tags: List<String> = emptyList(),
    /** 若由 ProjectTemplate 實例化則記錄 snapshot */
    val templateRef: TemplateRef? = null,
    val history: List<AuditEntry> = emptyList(),
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)
