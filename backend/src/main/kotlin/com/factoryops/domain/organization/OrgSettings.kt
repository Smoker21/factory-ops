package com.factoryops.domain.organization

/**
 * Organization root 節點設定 Value Object。
 * 對應 spec §4.1 OrgSettings;僅 root org 節點有此欄位。
 */
data class OrgSettings(
    /** Organization 樹最大深度(root = 0;預設 5) */
    val orgMaxDepth: Int = 5,
    /** 哪些 type code 視為 leaf(可承載 Group / Project / Task;預設 ["SECTION"]) */
    val leafTypes: List<String> = listOf("SECTION"),
    /** 附件檔案大小上限(bytes;預設 50 MB) */
    val attachmentMaxBytes: Long = 52428800L,
    /** 預留自由擴充欄位 */
    val extras: Map<String, Any?> = emptyMap()
)
