package com.factoryops.unit

import com.factoryops.infrastructure.hr.MockHrClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for MockHrClient — verifies the fixed set of seeded employees.
 */
class MockHrClientTest {

    private lateinit var hrClient: MockHrClient

    @BeforeEach
    fun setup() {
        hrClient = MockHrClient()
    }

    // ─── findByAccountName ────────────────────────────────────────────────────────

    @Test
    fun `findByAccountName returns employee for known account`() {
        // When
        val result = hrClient.findByAccountName("admin.system")

        // Then
        assertNotNull(result)
        assertEquals("admin.system", result?.accountName)
        assertEquals("EMP-00001", result?.employeeNo)
        assertEquals("System Admin", result?.displayName)
        assertEquals("admin@factory.example.com", result?.email)
        assertTrue(result?.active == true)
        assertTrue(result?.defaultRoles?.contains("ADMIN") == true)
    }

    @Test
    fun `findByAccountName returns manager employee`() {
        // When
        val result = hrClient.findByAccountName("manager.wang")

        // Then
        assertNotNull(result)
        assertEquals("manager.wang", result?.accountName)
        assertEquals("EMP-00002", result?.employeeNo)
        assertEquals("王廠長", result?.displayName)
        assertTrue(result?.defaultRoles?.contains("ORG_ADMIN") == true)
    }

    @Test
    fun `findByAccountName returns leader employee with multiple roles`() {
        // When
        val result = hrClient.findByAccountName("leader.chen")

        // Then
        assertNotNull(result)
        assertEquals("EMP-00003", result?.employeeNo)
        assertEquals(2, result?.defaultRoles?.size)
        assertTrue(result?.defaultRoles?.contains("SHIFT_LEAD") == true)
        assertTrue(result?.defaultRoles?.contains("GROUP_MANAGER") == true)
    }

    @Test
    fun `findByAccountName returns operator employee`() {
        // When
        val result = hrClient.findByAccountName("operator.li")

        // Then
        assertNotNull(result)
        assertEquals("EMP-00004", result?.employeeNo)
        assertEquals("李作業員", result?.displayName)
        assertTrue(result?.defaultRoles?.contains("OPERATOR") == true)
    }

    @Test
    fun `findByAccountName returns QA employee`() {
        // When
        val result = hrClient.findByAccountName("qa.zhang")

        // Then
        assertNotNull(result)
        assertEquals("EMP-00005", result?.employeeNo)
        assertTrue(result?.defaultRoles?.contains("QA") == true)
        assertTrue(result?.defaultRoles?.contains("ENGINEER") == true)
    }

    @Test
    fun `findByAccountName returns null for unknown account`() {
        // When
        val result = hrClient.findByAccountName("nonexistent.user")

        // Then
        assertNull(result)
    }

    // ─── isActiveEmployee ─────────────────────────────────────────────────────────

    @Test
    fun `isActiveEmployee returns true for known active employee`() {
        // When / Then
        assertTrue(hrClient.isActiveEmployee("admin.system"))
    }

    @Test
    fun `isActiveEmployee returns true for operator employee`() {
        // When / Then
        assertTrue(hrClient.isActiveEmployee("operator.li"))
    }

    @Test
    fun `isActiveEmployee returns false for unknown account`() {
        // When / Then
        assertFalse(hrClient.isActiveEmployee("unknown.account"))
    }
}
