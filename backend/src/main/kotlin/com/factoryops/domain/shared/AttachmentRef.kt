package com.factoryops.domain.shared

/**
 * 附件引用 Value Object。
 * 對應 spec §5 AttachmentRef VO 及 openapi.yaml components/schemas/AttachmentRef。
 * 儲存在 Task / ActionRequest / Comment 內;實際檔案在 attachments collection + MinIO(ADR-0003)。
 */
data class AttachmentRef(
    /** 附件 ID(ObjectId hex string),ref to attachments collection */
    val attachmentId: String,
    /** 替代文字(供無障礙閱讀 / 圖片說明) */
    val alt: String? = null,
    /** 附件角色 */
    val role: AttachmentRole = AttachmentRole.ATTACHMENT
)

/**
 * 附件在所屬資源中的角色。
 */
enum class AttachmentRole {
    /** 內嵌於 Markdown 正文 */
    INLINE,
    /** 附件(不內嵌) */
    ATTACHMENT,
    /** 封面圖 */
    COVER
}
