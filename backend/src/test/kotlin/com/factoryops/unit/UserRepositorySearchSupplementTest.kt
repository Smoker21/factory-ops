package com.factoryops.unit

import com.factoryops.persistence.repository.UserRepository
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * S-013 supplementary unit tests for UserRepository.searchByKeyword.
 *
 * Supplements UserRepositorySearchTest with additional regex-metacharacter and
 * edge-case scenarios called for in the M5.3.2 brief.
 *
 * All tests that involve actual DB calls catch the resulting exception and assert
 * it is NOT an IllegalArgumentException — proving validation passed.
 */
class UserRepositorySearchSupplementTest {

    private val repo = UserRepository()

    // ─── Additional regex metacharacters: should NOT be rejected ──────────────────

    @Test
    fun `searchByKeyword should accept keyword with dot (regex metachar)`() {
        assertPassesValidation("user.name")
    }

    @Test
    fun `searchByKeyword should accept keyword with parentheses`() {
        assertPassesValidation("dept(old)")
    }

    @Test
    fun `searchByKeyword should accept keyword with square brackets`() {
        assertPassesValidation("admin[1]")
    }

    @Test
    fun `searchByKeyword should accept keyword with caret (potential anchor metachar)`() {
        assertPassesValidation("^admin")
    }

    @Test
    fun `searchByKeyword should accept keyword with dollar sign`() {
        assertPassesValidation("user\$name")
    }

    @Test
    fun `searchByKeyword should accept keyword with backslash`() {
        assertPassesValidation("path\\to\\user")
    }

    @Test
    fun `searchByKeyword should accept keyword with plus sign (regex quantifier)`() {
        assertPassesValidation("user+name")
    }

    // ─── SQL-injection-like strings: safe due to Pattern.quote ────────────────────

    @Test
    fun `searchByKeyword should accept SQL injection style string`() {
        // "'; DROP TABLE users; --" contains only non-wildcard chars
        assertPassesValidation("'; DROP TABLE users")
    }

    @Test
    fun `searchByKeyword should accept regex ReDoS attempt`() {
        // "a+a+a+a+a+a+a+" — would cause catastrophic backtracking if not escaped
        assertPassesValidation("a+a+a+a+a+a+a+")
    }

    // ─── Unicode / Chinese input ───────────────────────────────────────────────────

    @Test
    fun `searchByKeyword should accept Chinese characters`() {
        assertPassesValidation("台中廠管理員")
    }

    @Test
    fun `searchByKeyword should accept mixed Chinese and Latin`() {
        assertPassesValidation("admin台中")
    }

    // ─── Length boundary edge cases ───────────────────────────────────────────────

    @Test
    fun `searchByKeyword should accept single character keyword`() {
        assertPassesValidation("a")
    }

    @Test
    fun `searchByKeyword should reject keyword of length 65`() {
        val longKeyword = "a".repeat(65)
        assertThrows<IllegalArgumentException> {
            repo.searchByKeyword(ObjectId(), longKeyword)
        }
    }

    @Test
    fun `searchByKeyword should reject keyword of length 100`() {
        val longKeyword = "a".repeat(100)
        assertThrows<IllegalArgumentException> {
            repo.searchByKeyword(ObjectId(), longKeyword)
        }
    }

    // ─── Wildcard rejection ────────────────────────────────────────────────────────

    @Test
    fun `searchByKeyword should reject keyword containing only asterisk`() {
        assertThrows<IllegalArgumentException> {
            repo.searchByKeyword(ObjectId(), "*")
        }
    }

    @Test
    fun `searchByKeyword should reject keyword containing only question mark`() {
        assertThrows<IllegalArgumentException> {
            repo.searchByKeyword(ObjectId(), "?")
        }
    }

    @Test
    fun `searchByKeyword should reject keyword where asterisk appears in the middle`() {
        assertThrows<IllegalArgumentException> {
            repo.searchByKeyword(ObjectId(), "admin*user")
        }
    }

    // ─── Helper: asserts validation passes (actual DB call may fail) ──────────────

    private fun assertPassesValidation(keyword: String) {
        try {
            repo.searchByKeyword(ObjectId(), keyword)
        } catch (ex: Exception) {
            assert(ex !is IllegalArgumentException) {
                "searchByKeyword should NOT throw IllegalArgumentException for '$keyword', got: $ex"
            }
        }
    }
}
