package com.factoryops.unit

import com.factoryops.persistence.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for S-013 UserRepository.searchByKeyword safety constraints.
 * These tests verify the argument validation in searchByKeyword — not the DB query itself.
 */
class UserRepositorySearchTest {

    private val repository = UserRepository()

    @Test
    fun `searchByKeyword should reject keyword longer than 64 characters`() {
        val longKeyword = "a".repeat(65)
        assertThrows<IllegalArgumentException> {
            repository.searchByKeyword(org.bson.types.ObjectId(), longKeyword)
        }
    }

    @Test
    fun `searchByKeyword should reject keyword with asterisk wildcard`() {
        assertThrows<IllegalArgumentException> {
            repository.searchByKeyword(org.bson.types.ObjectId(), "admin*")
        }
    }

    @Test
    fun `searchByKeyword should reject keyword with question mark wildcard`() {
        assertThrows<IllegalArgumentException> {
            repository.searchByKeyword(org.bson.types.ObjectId(), "admin?")
        }
    }

    @Test
    fun `searchByKeyword should accept keyword with exactly 64 characters`() {
        val maxKeyword = "a".repeat(64)
        // Does not throw - validation passes; actual DB call would fail (no DB in unit tests)
        // We only verify the validation gate does not block a 64-char keyword
        try {
            repository.searchByKeyword(org.bson.types.ObjectId(), maxKeyword)
        } catch (ex: Exception) {
            // DB-related exception is expected (no MongoDB in unit test context)
            // We only care that the IllegalArgumentException is NOT thrown
            assert(ex !is IllegalArgumentException) {
                "Should not throw IllegalArgumentException for 64-char keyword, got: $ex"
            }
        }
    }

    @Test
    fun `searchByKeyword should accept keyword with special regex characters`() {
        // Dots, parens, brackets are regex metacharacters — should be Pattern.quoted not rejected
        try {
            repository.searchByKeyword(org.bson.types.ObjectId(), "user.name (test)")
        } catch (ex: Exception) {
            // DB-related exception expected
            assert(ex !is IllegalArgumentException)
        }
    }
}
