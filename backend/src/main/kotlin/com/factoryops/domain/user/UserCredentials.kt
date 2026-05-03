package com.factoryops.domain.user

import java.time.Instant

/**
 * 使用者密碼憑證 aggregate root。
 * 對應 MongoDB collection: user_credentials
 *
 * 與 User aggregate 分離儲存的原因(設計需求第 7 點):
 * - GDPR 合規:離職時可清除密碼 hash 而保留業務記錄(User document)。
 * - 安全:密碼 hash 不進任何業務 log 或 audit trail。
 * - 獨立 rotate:密碼策略變更時只需更新此 collection。
 *
 * 注意:此 document 欄位不得出現在任何 log 輸出。
 * 注意:未來接 SSO/HR 提供身份驗證時,此 collection 可能廢棄;保留供本地驗證使用。
 */
data class UserCredentials(
    val id: String? = null,
    /** 1:1 對應 users._id */
    val userId: String,
    val rootOrgId: String,
    /**
     * 密碼 hash(argon2id 格式)。
     * 此欄位不得進 log / audit trail / serialization 至前端。
     */
    val passwordHash: String,
    /** hash 演算法識別(目前固定 ARGON2ID) */
    val algorithm: String = "ARGON2ID",
    val updatedAt: Instant = Instant.now(),
    val schemaVersion: Int = 1
)
