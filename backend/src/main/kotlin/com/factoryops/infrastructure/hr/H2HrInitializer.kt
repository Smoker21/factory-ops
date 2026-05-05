package com.factoryops.infrastructure.hr

import io.agroal.api.AgroalDataSource
import io.quarkus.agroal.DataSource
import io.quarkus.arc.lookup.LookupIfProperty
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection

private val logger = KotlinLogging.logger {}

/**
 * Bootstraps the H2 HR datasource when hr.mode=h2:
 *   1. (Re)creates the hr_employees table (idempotent).
 *   2. Truncates and reloads rows from the configured CSV file.
 *
 * Filesystem CSV path is preferred (allows editing without rebuild);
 * falls back to a classpath resource of the same name. If neither is
 * found, the table is left empty and a warning is logged.
 */
@ApplicationScoped
@LookupIfProperty(name = "hr.mode", stringValue = "h2")
class H2HrInitializer @Inject constructor(
    @DataSource("hr") private val hrDataSource: AgroalDataSource,
    @ConfigProperty(name = "hr.h2.csv.path", defaultValue = "test_data/hr_employees.csv")
    private val csvPath: String,
    @ConfigProperty(name = "hr.h2.csv.classpath-fallback", defaultValue = "test_data/hr_employees.csv")
    private val classpathFallback: String,
) {

    fun onStart(@Observes startupEvent: StartupEvent) {
        hrDataSource.connection.use { conn ->
            createSchema(conn)
            val rows = readCsvRows()
            if (rows.isEmpty()) {
                logger.warn { "H2 HR initializer: no CSV rows found at '$csvPath' or classpath '$classpathFallback'; table left empty" }
                return@use
            }
            truncate(conn)
            insertAll(conn, rows)
            logger.info { "H2 HR initializer: loaded ${rows.size} employee row(s) from CSV" }
        }
    }

    private fun createSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS hr_employees (
                    account_name   VARCHAR(30)  PRIMARY KEY,
                    employee_no    VARCHAR(20)  NOT NULL,
                    display_name   VARCHAR(100) NOT NULL,
                    email          VARCHAR(255),
                    active         BOOLEAN      NOT NULL,
                    default_roles  VARCHAR(500) NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun truncate(conn: Connection) {
        conn.createStatement().use { it.executeUpdate("TRUNCATE TABLE hr_employees") }
    }

    private fun insertAll(conn: Connection, rows: List<CsvRow>) {
        val sql = """
            INSERT INTO hr_employees (account_name, employee_no, display_name, email, active, default_roles)
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { ps ->
            for (row in rows) {
                ps.setString(1, row.accountName)
                ps.setString(2, row.employeeNo)
                ps.setString(3, row.displayName)
                if (row.email.isNullOrBlank()) ps.setNull(4, java.sql.Types.VARCHAR) else ps.setString(4, row.email)
                ps.setBoolean(5, row.active)
                ps.setString(6, row.defaultRoles)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun readCsvRows(): List<CsvRow> {
        val fsPath = Path.of(csvPath)
        return when {
            Files.exists(fsPath) -> {
                logger.info { "H2 HR initializer: reading CSV from filesystem '${fsPath.toAbsolutePath()}'" }
                Files.newBufferedReader(fsPath, StandardCharsets.UTF_8).use { parseCsv(it) }
            }
            else -> {
                val classpathStream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream(classpathFallback)
                if (classpathStream == null) {
                    emptyList()
                } else {
                    logger.info { "H2 HR initializer: filesystem CSV missing, reading from classpath '$classpathFallback'" }
                    BufferedReader(InputStreamReader(classpathStream, StandardCharsets.UTF_8)).use { parseCsv(it) }
                }
            }
        }
    }

    private fun parseCsv(reader: BufferedReader): List<CsvRow> {
        val lines = reader.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val header = splitCsvLine(lines.first()).map { it.trim() }
        val expected = listOf("account_name", "employee_no", "display_name", "email", "active", "default_roles")
        require(header == expected) {
            "Invalid HR CSV header. Expected $expected but got $header"
        }

        return lines.drop(1).mapIndexedNotNull { idx, raw ->
            val cols = splitCsvLine(raw)
            if (cols.size != expected.size) {
                logger.warn { "Skipping malformed CSV row ${idx + 2}: $raw" }
                return@mapIndexedNotNull null
            }
            CsvRow(
                accountName = cols[0].trim(),
                employeeNo = cols[1].trim(),
                displayName = cols[2].trim(),
                email = cols[3].trim().ifBlank { null },
                active = cols[4].trim().equals("true", ignoreCase = true),
                defaultRoles = cols[5].trim(),
            )
        }
    }

    /**
     * Minimal CSV splitter — fields separated by comma, optional double-quote wrapping.
     * Newlines inside quoted fields are not supported (the test data does not need it).
     */
    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private data class CsvRow(
        val accountName: String,
        val employeeNo: String,
        val displayName: String,
        val email: String?,
        val active: Boolean,
        val defaultRoles: String,
    )
}
