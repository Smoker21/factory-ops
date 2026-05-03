package com.factoryops.domain.group

/**
 * Group 多型 discriminator 內建值。
 * 對應 openapi.yaml components/schemas/GroupType。
 * Group 為平面結構,無 parentId。
 * 自訂 type 可擴充;domain class 使用 String 儲存。
 */
enum class GroupType(val code: String) {
    DEFAULT("DEFAULT"),
    LINE("LINE"),
    TEAM("TEAM"),
    SHIFT("SHIFT");

    companion object {
        fun fromCode(code: String): GroupType? = entries.find { it.code == code }
    }
}
