package com.factoryops.interfaces.filter

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import mu.KotlinLogging
import org.eclipse.microprofile.jwt.JsonWebToken

private val logger = KotlinLogging.logger {}

/**
 * JAX-RS request filter that extracts rootOrgId and other claims from the JWT
 * and populates the CDI request-scoped RequestContext.
 *
 * This enforces multi-tenant isolation (ADR-0005): all subsequent service calls
 * can rely on RequestContext.requireRootOrgId() to get the scoped tenant.
 */
@Provider
@ApplicationScoped
class OrgScopeFilter : ContainerRequestFilter {

    @Inject
    lateinit var jwt: JsonWebToken

    @Inject
    lateinit var requestContext: RequestContext

    override fun filter(ctx: ContainerRequestContext) {
        val path = ctx.uriInfo.path
        // Skip public endpoints
        if (path.startsWith("/q/") || path == "/v1/health" || path == "/health" ||
            path == "/v1/auth/login" || path == "/auth/login" ||
            path == "/v1/auth/refresh" || path == "/auth/refresh" ||
            path.startsWith("/mock-hr")) {
            return
        }

        try {
            val rootOrgId = jwt.getClaim<String>("rootOrgId")
            val userId = jwt.getClaim<String>("userId")
            val accountName = jwt.getClaim<String>("upn") ?: jwt.getClaim<String>("accountName") ?: jwt.name
            @Suppress("UNCHECKED_CAST")
            val roles = (jwt.getClaim<Any>("groups") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val orgManagerScopes = (jwt.getClaim<Any>("orgManagerScopes") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val groupIds = (jwt.getClaim<Any>("groupIds") as? List<String>) ?: emptyList()

            if (rootOrgId != null) {
                requestContext.rootOrgId = rootOrgId
                requestContext.userId = userId
                requestContext.accountName = accountName
                requestContext.roles = roles
                requestContext.orgManagerScopes = orgManagerScopes
                requestContext.groupIds = groupIds
            }
        } catch (ex: Exception) {
            logger.debug { "JWT not present or invalid for path $path: ${ex.message}" }
        }
    }
}
