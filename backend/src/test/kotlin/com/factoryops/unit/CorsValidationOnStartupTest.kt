package com.factoryops.unit

import com.factoryops.infrastructure.security.CorsValidationOnStartup
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for S-010: CorsValidationOnStartup.
 * Verifies that prod profile with insecure CORS config aborts startup.
 *
 * Note: The profile injection (quarkus.profile) itself is not tested here
 * (that's a CDI concern); this test verifies the validation logic directly
 * by setting the fields.
 */
class CorsValidationOnStartupTest {

    private fun bean(profile: String, origins: String): CorsValidationOnStartup {
        val b = CorsValidationOnStartup()
        b.quarkusProfile = profile
        b.corsOrigins = origins
        return b
    }

    private val startupEvent = StartupEvent()

    @Test
    fun `should throw when prod profile and CORS origins is empty`() {
        val b = bean("prod", "")
        val ex = assertThrows<RuntimeException> { b.onStart(startupEvent) }
        assert(ex.message!!.contains("CORS_ORIGINS"))
    }

    @Test
    fun `should throw when prod profile and CORS origins is wildcard star`() {
        val b = bean("prod", "*")
        val ex = assertThrows<RuntimeException> { b.onStart(startupEvent) }
        assert(ex.message!!.contains("wildcard"))
    }

    @Test
    fun `should throw when prod profile and CORS origins is whitespace only`() {
        val b = bean("prod", "   ")
        val ex = assertThrows<RuntimeException> { b.onStart(startupEvent) }
        assert(ex.message!!.contains("CORS_ORIGINS"))
    }

    @Test
    fun `should not throw when prod profile and CORS origins is set`() {
        val b = bean("prod", "https://factory-ops.example.com")
        assertDoesNotThrow { b.onStart(startupEvent) }
    }

    @Test
    fun `should not throw when dev profile regardless of CORS origins`() {
        val b = bean("dev", "")
        assertDoesNotThrow { b.onStart(startupEvent) }
    }

    @Test
    fun `should not throw when dev profile with wildcard`() {
        val b = bean("dev", "*")
        assertDoesNotThrow { b.onStart(startupEvent) }
    }
}
