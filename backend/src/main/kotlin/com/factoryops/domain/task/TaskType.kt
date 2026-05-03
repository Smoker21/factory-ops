package com.factoryops.domain.task

/**
 * Task 多型 type 內建值常數。
 * 對應 spec §8 多型策略 + openapi.yaml TaskTypeDefinition。
 *
 * Task.type 以 String 儲存於 MongoDB(polymorphic discriminator),
 * 此 object 僅提供常數參照;自訂 type 可自由擴充(無需修改此檔案)。
 */
object TaskType {
    const val EQUIPMENT_INSPECTION = "EQUIPMENT_INSPECTION"
    const val INCIDENT_RESPONSE = "INCIDENT_RESPONSE"
    const val SHIFT_HANDOVER = "SHIFT_HANDOVER"
    const val MAINTENANCE = "MAINTENANCE"
    const val GENERAL = "GENERAL"
}
