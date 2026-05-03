package com.factoryops.domain.attachment

import java.time.Instant

/**
 * Attachment aggregate root(metadata only;實際檔案在 MinIO,ADR-0003)。
 * 對應 spec §2 Attachment aggregate + openapi.yaml components/schemas/Attachment。
 * 對應 MongoDB collection: attachments
 *
 * 設計重點:
 * - 僅儲存 metadata;檔案本體在 MinIO Object Storage。
 * - Task / ActionRequest / Comment 內存 AttachmentRef(ID + alt + role)。
 * - `storageKey`:MinIO object key,不對前端暴露。
 * - `downloadUrl`:由 API 即時生成 presigned URL,不持久化儲存。
 * - `status`:PENDING_UPLOAD → READY(finalize 後)/ FAILED / DELETED。
 */
data class Attachment(
    val id: String? = null,
    val rootOrgId: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** MinIO object key;不對前端暴露 */
    val storageKey: String,
    val uploaderId: String,
    /** 所屬資源型別;可 null(上傳時尚未關聯) */
    val ownerResourceType: OwnerResourceType? = null,
    /** 所屬資源 ID;可 null */
    val ownerResourceId: String? = null,
    val status: AttachmentStatus = AttachmentStatus.PENDING_UPLOAD,
    val metadata: MediaMetadata? = null,
    val schemaVersion: Int = 1,
    val createdAt: Instant = Instant.now(),
    val deletedAt: Instant? = null
)

/**
 * 附件所屬資源型別。
 * 對應 openapi.yaml CreateAttachmentRequest.ownerResourceType。
 */
enum class OwnerResourceType {
    TASK,
    ACTION_REQUEST,
    COMMENT,
    PROJECT
}

/**
 * 附件處理狀態。
 * 對應 openapi.yaml components/schemas/Attachment.status。
 */
enum class AttachmentStatus {
    PENDING_UPLOAD,
    READY,
    FAILED,
    DELETED
}

/**
 * 媒體元資料 Value Object。
 * 對應 spec §5 MediaMetadata VO。
 */
data class MediaMetadata(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Int? = null,
    val checksum: String? = null
)
