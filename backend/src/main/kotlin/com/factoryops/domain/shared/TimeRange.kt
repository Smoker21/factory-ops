package com.factoryops.domain.shared

import java.time.Instant

/**
 * 時間範圍 Value Object。
 * 對應 spec §5 TimeRange VO。
 * Invariant:若 start 與 due 皆設定,則 due > start(由 application service 在寫入前驗證)。
 */
data class TimeRange(
    /** 開始時間(UTC Instant);可 null 表示未設定 */
    val start: Instant? = null,
    /** 截止時間(UTC Instant);可 null 表示未設定 */
    val due: Instant? = null
)
