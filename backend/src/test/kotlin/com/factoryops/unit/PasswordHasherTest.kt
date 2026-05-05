package com.factoryops.unit

import com.factoryops.application.auth.PasswordHasher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for PasswordHasher — BCrypt hash/verify functionality.
 */
class PasswordHasherTest {

    private lateinit var passwordHasher: PasswordHasher

    @BeforeEach
    fun setup() {
        passwordHasher = PasswordHasher()
    }

    @Test
    fun `hash returns non-empty hash string`() {
        // Given
        val plaintext = "TestPassword@123"

        // When
        val hash = passwordHasher.hash(plaintext)

        // Then
        assertTrue(hash.isNotEmpty())
        assertTrue(hash.startsWith("\$2a\$") || hash.startsWith("\$2b\$"), "Hash should be BCrypt format")
    }

    @Test
    fun `hash produces different output each time for same input`() {
        // Given
        val plaintext = "TestPassword@123"

        // When
        val hash1 = passwordHasher.hash(plaintext)
        val hash2 = passwordHasher.hash(plaintext)

        // Then: BCrypt uses salt, so hashes should differ
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verify returns true for correct password`() {
        // Given
        val plaintext = "TestPassword@123"
        val hash = passwordHasher.hash(plaintext)

        // When
        val result = passwordHasher.verify(plaintext, hash)

        // Then
        assertTrue(result)
    }

    @Test
    fun `verify returns false for wrong password`() {
        // Given
        val plaintext = "TestPassword@123"
        val hash = passwordHasher.hash(plaintext)

        // When
        val result = passwordHasher.verify("WrongPassword@123", hash)

        // Then
        assertFalse(result)
    }

    @Test
    fun `verify returns false for empty string against valid hash`() {
        // Given
        val hash = passwordHasher.hash("SomePassword@123")

        // When
        val result = passwordHasher.verify("", hash)

        // Then
        assertFalse(result)
    }
}
