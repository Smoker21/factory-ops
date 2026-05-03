package com.factoryops.domain.shared.enums

/**
 * Template 範圍枚舉。
 * 對應 openapi.yaml components/schemas/TemplateScope。
 * - GLOBAL:由 ADMIN 維護,所有 Org 可見
 * - ORG:屬某個 root org,僅該 root org 可見
 *
 * GLOBAL Template 不可改為 ORG scope;ORG 亦然(只能 fork)。見 ADR-0006。
 */
enum class TemplateScope {
    GLOBAL,
    ORG
}
