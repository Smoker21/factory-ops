package com.factoryops.domain.template

import com.factoryops.domain.shared.enums.TemplateScope

/**
 * ProjectTemplate 中對 TaskTemplate 的引用 Value Object。
 * 對應 spec §5 TaskTemplateRef VO + openapi.yaml components/schemas/TaskTemplateRef。
 * embed 在 ProjectTemplate.taskTemplateRefs[]。
 */
data class TaskTemplateRef(
    /** TaskTemplate scope */
    val taskTemplateScope: TemplateScope,
    /** TaskTemplate ID(ObjectId hex string) */
    val taskTemplateId: String,
    /** 引用的 version */
    val version: Int,
    /** 在 ProjectTemplate 中的排序 */
    val sortOrder: Int,
    /** 預估工時(小時) */
    val estimatedHours: Float? = null
)
