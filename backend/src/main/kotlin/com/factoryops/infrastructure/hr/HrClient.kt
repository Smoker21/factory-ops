package com.factoryops.infrastructure.hr

/**
 * HR system integration interface.
 * DEV mode: MockHrClient returns fixed test data.
 * PROD mode: Real implementation calls the corporate HR REST API.
 */
interface HrClient {

    /**
     * Looks up an employee by accountName.
     * Returns null if employee not found or HR service is unavailable.
     */
    fun findByAccountName(accountName: String): HrEmployee?

    /**
     * Validates that an employee exists and is currently active (not terminated).
     */
    fun isActiveEmployee(accountName: String): Boolean
}

data class HrEmployee(
    val accountName: String,
    val employeeNo: String,
    val displayName: String,
    val email: String?,
    val active: Boolean,
    val defaultRoles: List<String> = listOf("OPERATOR")
)
