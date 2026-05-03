package com.factoryops.domain.template

import java.time.Instant

/**
 * Fork 來源快照 Value Object。
 * 對應 spec §5 ForkRef VO。
 * 記錄 ORG Template 是從哪個 GLOBAL Template fork 而來。
 * Fork 後完全解耦(ADR-0006)。
 */
data class ForkRef(
    /** 來源 Template ID(ObjectId hex string) */
    val sourceTemplateId: String,
    /** 來源 version */
    val sourceVersion: Int,
    /** fork 時間(UTC Instant) */
    val forkedAt: Instant,
    /** fork 操作者 userId */
    val forkedBy: String
)
