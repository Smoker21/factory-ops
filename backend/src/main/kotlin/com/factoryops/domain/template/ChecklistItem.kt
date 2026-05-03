package com.factoryops.domain.template

/**
 * 檢查清單項目 Value Object。
 * 對應 spec §5 ChecklistItem VO + openapi.yaml components/schemas/ChecklistItem。
 * 用於 TaskTemplate.defaultChecklist。
 */
data class ChecklistItem(
    /** 項目 ID(UUID/ULID,在 template document 內唯一) */
    val id: String,
    /** 項目說明 */
    val label: String,
    /** 是否為必填項目 */
    val required: Boolean = false
)
