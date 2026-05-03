package com.factoryops.domain.project

/**
 * Project 狀態枚舉。
 * 對應 openapi.yaml components/schemas/ProjectStatus。
 *
 * 合法轉換(application service 強制):
 * DRAFT → ACTIVE → PAUSED → ACTIVE(恢復) / COMPLETED / CANCELLED
 * DRAFT → CANCELLED
 * 進入 COMPLETED 或 CANCELLED 後不允許新增 Task / ActionRequest。
 */
enum class ProjectStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED
}
