package com.factoryops.hr

import com.factoryops.infrastructure.hr.H2HrClient
import com.factoryops.infrastructure.hr.HrClient
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.TestProfile
import io.quarkus.test.junit.QuarkusTestProfile
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration test for the H2-backed HR client.
 *
 * Activates the h2 backend via [H2HrModeProfile] so the [HrClient] CDI lookup
 * resolves to [H2HrClient] (instead of the default MockHrClient).  Verifies
 * that test_data/hr_employees.csv was loaded and that lookups behave correctly.
 */
@QuarkusTest
@TestProfile(H2HrClientIntegrationTest.H2HrModeProfile::class)
class H2HrClientIntegrationTest {

    @Inject
    lateinit var hrClient: HrClient

    @Test
    fun `injected client is the H2-backed implementation when hr_mode is h2`() {
        assertTrue(hrClient is H2HrClient, "Expected H2HrClient but got ${hrClient::class.qualifiedName}")
    }

    @Test
    fun `findByAccountName returns active operator from CSV`() {
        val emp = hrClient.findByAccountName("operator.li")
        assertNotNull(emp)
        emp!!
        assertEquals("EMP-00013", emp.employeeNo)
        assertEquals("李作業員", emp.displayName)
        assertTrue(emp.active)
        assertEquals(listOf("OPERATOR"), emp.defaultRoles)
    }

    @Test
    fun `findByAccountName splits multi-role default_roles by semicolon`() {
        val emp = hrClient.findByAccountName("leader.chen")
        assertNotNull(emp)
        assertEquals(listOf("SHIFT_LEAD", "GROUP_MANAGER"), emp!!.defaultRoles)
    }

    @Test
    fun `findByAccountName returns null email for terminated employee`() {
        val emp = hrClient.findByAccountName("former.huang")
        assertNotNull(emp)
        assertFalse(emp!!.active)
        assertNull(emp.email)
    }

    @Test
    fun `findByAccountName returns null for unknown account`() {
        val emp = hrClient.findByAccountName("nobody.exists")
        assertNull(emp)
    }

    @Test
    fun `isActiveEmployee distinguishes active and terminated`() {
        assertTrue(hrClient.isActiveEmployee("admin.system"))
        assertFalse(hrClient.isActiveEmployee("former.huang"))
        assertFalse(hrClient.isActiveEmployee("nobody.exists"))
    }

    class H2HrModeProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> = mapOf(
            "hr.mode" to "h2",
            // Force classpath fallback so the test does not depend on the working directory
            "hr.h2.csv.path" to "/nonexistent-on-purpose.csv",
            "hr.h2.csv.classpath-fallback" to "test_data/hr_employees.csv",
        )
    }
}
