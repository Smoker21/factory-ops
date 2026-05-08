package com.factoryops.unit

import com.factoryops.application.service.generateTemporaryPassword
import com.factoryops.application.service.validatePasswordStrength
import com.factoryops.interfaces.exception.ValidationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * S-011: Additional boundary tests for validatePasswordStrength and generateTemporaryPassword.
 *
 * Supplements SecurityUtilsTest with explicit boundary cases called for in the M5.3.2 brief.
 */
class SecurityUtilsSupplementTest {

    // ─── validatePasswordStrength: character-class boundaries ────────────────────

    @Test
    fun `should pass for password with exactly 4 character classes`() {
        // uppercase + lowercase + digit + symbol (all 4 classes present)
        // "Valid@Pass1x" = 12 chars, uppercase, lowercase, digit, symbol
        validatePasswordStrength("Valid@Pass1x!")
    }

    @Test
    fun `should pass for exactly 12 chars with uppercase lowercase digit`() {
        // Exactly 12 characters, 3 classes: uppercase + lowercase + digit
        validatePasswordStrength("AbcDef123456")
    }

    @Test
    fun `should pass for exactly 12 chars with lowercase digit symbol`() {
        // 3 classes: lowercase + digit + symbol
        validatePasswordStrength("abcdef1234!!")
    }

    @Test
    fun `should fail for exactly 12 chars with only lowercase`() {
        // 12 chars but only 1 class — fails (< 3 classes)
        val ex = assertThrows<ValidationException> {
            validatePasswordStrength("abcdefghijkl")
        }
        assertTrue(ex.errorCode.contains("weak") || ex.message!!.contains("3"))
    }

    @Test
    fun `should fail for exactly 12 chars with only uppercase and lowercase (2 classes)`() {
        // exactly 12 chars, 2 classes only — fails
        val ex = assertThrows<ValidationException> {
            validatePasswordStrength("AbcDEFghIJKL")
        }
        assertTrue(ex.errorCode.contains("weak"))
    }

    @Test
    fun `should fail for password of exactly 11 characters even if strong`() {
        // 11 chars, all 4 classes — fails because length < 12
        val ex = assertThrows<ValidationException> {
            validatePasswordStrength("Valid@Pas1x")
        }
        assertTrue(ex.errorCode.contains("short") || ex.message!!.contains("12"))
    }

    @Test
    fun `should pass for 13 char password with 3 classes`() {
        // 13 chars, uppercase + lowercase + digit
        validatePasswordStrength("AbcDef1234567")
    }

    // ─── generateTemporaryPassword: allowed charset ────────────────────────────────

    @Test
    fun `generated passwords only contain characters from allowed charset`() {
        val allowedChars = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789@#${'$'}%&*+".toSet()
        repeat(50) {
            val pwd = generateTemporaryPassword()
            val invalidChars = pwd.filter { it !in allowedChars }
            assertTrue(invalidChars.isEmpty()) {
                "Password '$pwd' contains disallowed characters: $invalidChars"
            }
        }
    }

    @Test
    fun `generated password length is always exactly 16`() {
        // Verify across 50 generations, not just 1
        repeat(50) {
            val pwd = generateTemporaryPassword()
            assertEquals(16, pwd.length, "Password '$pwd' has unexpected length ${pwd.length}")
        }
    }
}
