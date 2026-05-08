package com.factoryops.unit

import com.factoryops.application.service.generateTemporaryPassword
import com.factoryops.application.service.validatePasswordStrength
import com.factoryops.interfaces.exception.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for S-011 security utilities:
 *   - validatePasswordStrength
 *   - generateTemporaryPassword
 */
class SecurityUtilsTest {

    // ─── validatePasswordStrength ────────────────────────────────────────────────

    @Test
    fun `should pass for strong password with all character classes`() {
        // >= 12 chars, uppercase + lowercase + digit + symbol
        validatePasswordStrength("StrongPass@123")
    }

    @Test
    fun `should pass for password with exactly 3 character classes`() {
        // lowercase + uppercase + digit (no symbol) — still passes (>= 3 of 4)
        validatePasswordStrength("StrongPass123456")
    }

    @Test
    fun `should pass for password with uppercase + digit + symbol`() {
        validatePasswordStrength("UPPERCASE123@@@!")
    }

    @Test
    fun `should fail for password shorter than 12 characters`() {
        val ex = assertThrows(ValidationException::class.java) {
            validatePasswordStrength("Short@1A")
        }
        assertTrue(ex.errorCode.contains("short") || ex.message!!.contains("12"))
    }

    @Test
    fun `should fail for password with only 2 character classes`() {
        // lowercase + digit only (2 classes — below threshold of 3)
        val ex = assertThrows(ValidationException::class.java) {
            validatePasswordStrength("alllower123456")
        }
        assertTrue(ex.errorCode.contains("weak") || ex.message!!.contains("3"))
    }

    @Test
    fun `should fail for password with only lowercase characters`() {
        val ex = assertThrows(ValidationException::class.java) {
            validatePasswordStrength("alllowernospecial")
        }
        assertTrue(ex.errorCode.contains("weak"))
    }

    @Test
    fun `should pass for exactly 12 character password with 3 classes`() {
        // exactly 12, uppercase + lowercase + digit
        validatePasswordStrength("AbcDef123456")
    }

    // ─── generateTemporaryPassword ───────────────────────────────────────────────

    @Test
    fun `generated password should be 16 characters`() {
        val pwd = generateTemporaryPassword()
        assertEquals(16, pwd.length)
    }

    @Test
    fun `generated password should not contain ambiguous characters`() {
        // Repeat to get statistical coverage
        repeat(100) {
            val pwd = generateTemporaryPassword()
            assertFalse(pwd.contains('0'), "Should not contain '0' (zero)")
            assertFalse(pwd.contains('O'), "Should not contain 'O' (capital O)")
            assertFalse(pwd.contains('l'), "Should not contain 'l' (lowercase L)")
            assertFalse(pwd.contains('1'), "Should not contain '1' (one)")
        }
    }

    @Test
    fun `generated passwords should be different on repeated calls`() {
        val passwords = (1..20).map { generateTemporaryPassword() }.toSet()
        // With a 16-char charset of 60+ chars, 20 passwords should all be distinct
        assertEquals(20, passwords.size, "Generated passwords should be unique")
    }
}
