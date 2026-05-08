package com.factoryops.infrastructure.security

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KotlinLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

private val logger = KotlinLogging.logger {}

/**
 * S-010: CORS fail-fast on prod startup.
 *
 * In production profile, if the CORS origins value is unset, empty, or the wildcard "*",
 * the application refuses to start with a clear error message listing the required env var.
 *
 * This prevents accidentally deploying with permissive CORS that allows any origin.
 *
 * Configuration:
 *   - Set CORS_ORIGINS env var to a comma-separated list of allowed origins.
 *   - Example: CORS_ORIGINS=https://factory-ops.example.com
 */
@ApplicationScoped
class CorsValidationOnStartup {

    @ConfigProperty(name = "quarkus.profile", defaultValue = "dev")
    lateinit var quarkusProfile: String

    @ConfigProperty(name = "quarkus.http.cors.origins", defaultValue = "")
    lateinit var corsOrigins: String

    fun onStart(@Observes event: StartupEvent) {
        if (quarkusProfile != "prod") {
            logger.debug { "CORS validation skipped for profile=[$quarkusProfile]" }
            return
        }

        val trimmed = corsOrigins.trim()
        if (trimmed.isEmpty() || trimmed == "*") {
            val message = buildString {
                append("FATAL: Production startup aborted — insecure CORS configuration detected.\n")
                append("  quarkus.http.cors.origins is ")
                append(if (trimmed.isEmpty()) "empty" else "set to wildcard '*'")
                append(".\n")
                append("  Set env var CORS_ORIGINS to a comma-separated list of allowed origins.\n")
                append("  Example: CORS_ORIGINS=https://factory-ops.example.com\n")
                append("  Refusing to start with permissive CORS in production.")
            }
            logger.error { message }
            throw RuntimeException(message)
        }

        logger.info { "CORS validation passed for profile=[$quarkusProfile] origins=[$trimmed]" }
    }
}
