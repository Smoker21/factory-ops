package com.factoryops.domain.actionrequest

/**
 * ActionRequest 狀態枚舉(v1.3:移除 RELAYED)。
 * 對應 openapi.yaml components/schemas/ActionRequestStatus。
 * 對應 spec §4.10 ActionRequest 狀態機。
 *
 * 合法轉換:
 * SUBMITTED → TRIAGED(convert-to-task)
 * SUBMITTED → REJECTED
 * SUBMITTED → RESOLVED(直接處理)
 * TRIAGED → IN_PROGRESS(linkedTask 進入 IN_PROGRESS)
 * IN_PROGRESS → RESOLVED(linkedTask DONE 或手動)
 *
 * v1.3 移除:RELAYED 子狀態(Single-hop Direct Dispatch,ADR-0008)。
 */
enum class ActionRequestStatus {
    SUBMITTED,
    TRIAGED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
}
