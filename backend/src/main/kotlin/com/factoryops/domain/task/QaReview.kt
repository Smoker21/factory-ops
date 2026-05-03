package com.factoryops.domain.task

import com.factoryops.domain.shared.enums.Role
import java.time.Instant

/**
 * QA 雙簽審核記錄 Value Object(append-only,v1.3 新增)。
 * 對應 spec §4.7 QaReviewEntry + openapi.yaml components/schemas/QaReviewEntry。
 *
 * 預估每個 Task ≤ 5 筆,embed 在 Task document。
 *
 * Invariant(INV-36):同一 Task 中 (reviewerId, reviewerRole) 不可重複。
 * 同一 user 不可一次擔多個角色簽核同一 Task。
 *
 * REJECTED 時預設清空 Task.qaReviews[](重新走流程,Q-21 預設)。
 */
data class QaReview(
    /** reviewer 的 userId(ObjectId hex string) */
    val reviewerId: String,
    /** 此次 review 套用的角色;必須 ∈ qaReviewPolicy.requiredReviewerRoles */
    val reviewerRole: Role,
    /** 決議:APPROVED 或 REJECTED */
    val decision: ReviewDecision,
    /** 決議原因(REJECTED 時建議填寫) */
    val reason: String? = null,
    /** 簽核時間(UTC Instant) */
    val at: Instant = Instant.now()
)

/**
 * QA 審核決議枚舉。
 * 對應 openapi.yaml components/schemas/ReviewDecision。
 */
enum class ReviewDecision {
    APPROVED,
    REJECTED
}
