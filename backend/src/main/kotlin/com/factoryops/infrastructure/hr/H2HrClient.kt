package com.factoryops.infrastructure.hr

import io.agroal.api.AgroalDataSource
import io.quarkus.agroal.DataSource
import io.quarkus.arc.properties.IfBuildProperty
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * HR client backed by an H2 in-memory database seeded from test_data/hr_employees.csv.
 *
 * Activated when hr.mode=h2.  Schema and seed data are loaded by [H2HrInitializer]
 * during application startup.
 */
@ApplicationScoped
@IfBuildProperty(name = "hr.mode", stringValue = "h2")
class H2HrClient @Inject constructor(
    @DataSource("hr") private val hrDataSource: AgroalDataSource,
) : HrClient {

    override fun findByAccountName(accountName: String): HrEmployee? {
        val sql = """
            SELECT account_name, employee_no, display_name, email, active, default_roles
            FROM hr_employees
            WHERE account_name = ?
        """.trimIndent()

        hrDataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, accountName)
                ps.executeQuery().use { rs ->
                    return if (rs.next()) {
                        val email = rs.getString("email")
                        HrEmployee(
                            accountName = rs.getString("account_name"),
                            employeeNo = rs.getString("employee_no"),
                            displayName = rs.getString("display_name"),
                            email = if (rs.wasNull()) null else email,
                            active = rs.getBoolean("active"),
                            defaultRoles = rs.getString("default_roles")
                                .split(';')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() },
                        )
                    } else {
                        logger.debug { "H2HrClient: account_name '$accountName' not found" }
                        null
                    }
                }
            }
        }
    }

    override fun isActiveEmployee(accountName: String): Boolean {
        val sql = "SELECT active FROM hr_employees WHERE account_name = ?"
        hrDataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { ps ->
                ps.setString(1, accountName)
                ps.executeQuery().use { rs ->
                    return rs.next() && rs.getBoolean("active")
                }
            }
        }
    }
}
