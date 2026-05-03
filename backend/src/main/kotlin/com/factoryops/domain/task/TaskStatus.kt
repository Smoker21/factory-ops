package com.factoryops.domain.task

/**
 * Task 狀態枚舉。
 * 對應 openapi.yaml components/schemas/TaskStatus。
 * 對應 spec §4.10 Task 狀態機(v1.3 加 IN_REVIEW)。
 *
 * 合法轉換:
 * OPEN → IN_PROGRESS
 * IN_PROGRESS → BLOCKED / IN_REVIEW / DONE(dualSignRequired=false) / CANCELLED
 * IN_PROGRESS → DONE(GROUP_MANAGER force-complete,帶 bypassReason)
 * BLOCKED → IN_PROGRESS / CANCELLED
 * IN_REVIEW → IN_PROGRESS(review REJECTED;預設清空既往 reviews)
 * IN_REVIEW → DONE(蒐集完所有 required roles → server 自動推進)
 * OPEN / IN_PROGRESS / BLOCKED → CANCELLED
 */
enum class TaskStatus {
    OPEN,
    IN_PROGRESS,
    BLOCKED,
    IN_REVIEW,
    DONE,
    CANCELLED
}
