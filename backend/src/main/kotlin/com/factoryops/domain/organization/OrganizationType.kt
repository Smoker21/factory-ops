package com.factoryops.domain.organization

/**
 * Organization 多型 discriminator 內建值。
 * 對應 openapi.yaml components/schemas/OrganizationType。
 *
 * 注意:自訂 type 可由 root settings.leafTypes 配置,不限於此 enum;
 * 持久層以 String 儲存,domain class 使用 String 型別。
 * 此 enum 僅供程式碼中明確使用內建值時參照。
 */
enum class OrganizationType(val code: String) {
    /** 廠(最頂層) */
    FAB("FAB"),
    /** 處 */
    DIVISION("DIVISION"),
    /** 部 */
    DEPARTMENT("DEPARTMENT"),
    /** 課/組(預設 leaf type) */
    SECTION("SECTION");

    companion object {
        fun fromCode(code: String): OrganizationType? = entries.find { it.code == code }
    }
}
