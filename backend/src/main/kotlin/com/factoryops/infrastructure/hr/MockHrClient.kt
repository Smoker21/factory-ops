package com.factoryops.infrastructure.hr

import io.quarkus.arc.lookup.LookupIfProperty
import jakarta.enterprise.context.ApplicationScoped
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Mock HR client for development and testing.
 * Returns a small fixed set of test employees baked into the source code.
 *
 * Activated when hr.mode=mock (the default).  See [H2HrClient] for a richer,
 * CSV-driven alternative selected by hr.mode=h2.
 *
 * ADR-0007 / ADR-0014: HR backend selection via runtime feature toggle.
 */
@ApplicationScoped
@LookupIfProperty(name = "hr.mode", stringValue = "mock", lookupIfMissing = true)
class MockHrClient : HrClient {

    private val employees = mapOf(
        "admin.system" to HrEmployee(
            accountName = "admin.system",
            employeeNo = "EMP-00001",
            displayName = "System Admin",
            email = "admin@factory.example.com",
            active = true,
            defaultRoles = listOf("ADMIN")
        ),
        "manager.wang" to HrEmployee(
            accountName = "manager.wang",
            employeeNo = "EMP-00002",
            displayName = "王廠長",
            email = "manager.wang@factory.example.com",
            active = true,
            defaultRoles = listOf("ORG_ADMIN")
        ),
        "leader.chen" to HrEmployee(
            accountName = "leader.chen",
            employeeNo = "EMP-00003",
            displayName = "陳組長",
            email = "leader.chen@factory.example.com",
            active = true,
            defaultRoles = listOf("SHIFT_LEAD", "GROUP_MANAGER")
        ),
        "operator.li" to HrEmployee(
            accountName = "operator.li",
            employeeNo = "EMP-00004",
            displayName = "李作業員",
            email = "operator.li@factory.example.com",
            active = true,
            defaultRoles = listOf("OPERATOR")
        ),
        "qa.zhang" to HrEmployee(
            accountName = "qa.zhang",
            employeeNo = "EMP-00005",
            displayName = "張品管",
            email = "qa.zhang@factory.example.com",
            active = true,
            defaultRoles = listOf("QA", "ENGINEER")
        )
    )

    override fun findByAccountName(accountName: String): HrEmployee? {
        val result = employees[accountName]
        logger.debug { "MockHrClient.findByAccountName($accountName) -> ${result?.displayName ?: "NOT FOUND"}" }
        return result
    }

    override fun isActiveEmployee(accountName: String): Boolean =
        employees[accountName]?.active == true
}
