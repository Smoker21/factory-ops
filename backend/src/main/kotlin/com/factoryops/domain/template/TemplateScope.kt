package com.factoryops.domain.template

/**
 * Template 範圍定義位於 com.factoryops.domain.shared.enums.TemplateScope。
 *
 * GLOBAL:由 ADMIN 維護,所有 Org 可見。
 * ORG:屬某個 root org,僅該 root org 可見。
 *
 * 此檔案提供 package-level 文件,避免使用者找不到 TemplateScope 定義位置。
 * 實際 enum 定義請參閱 com.factoryops.domain.shared.enums.TemplateScope。
 * ProjectTemplate 與 TaskTemplate 直接 import shared.enums.TemplateScope 使用。
 */

// TemplateScope enum 定義在 shared/enums/TemplateScope.kt
// 此為刻意設計:TemplateScope 也被 AttachmentRef(TemplateRef)、任何 snapshot VO 使用,
// 故放在 shared/enums 而非 template package,避免循環引用。
