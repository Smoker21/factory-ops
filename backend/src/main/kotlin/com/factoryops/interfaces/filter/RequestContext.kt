package com.factoryops.interfaces.filter

import jakarta.enterprise.context.RequestScoped

/**
 * CDI request-scoped bean holding the resolved rootOrgId from JWT claims.
 * Populated by OrgScopeFilter from the JWT token.
 * All repository queries use this to enforce multi-tenant isolation (ADR-0005).
 */
@RequestScoped
class RequestContext {
    var rootOrgId: String? = null
    var userId: String? = null
    var accountName: String? = null
    var roles: List<String> = emptyList()
    var orgManagerScopes: List<String> = emptyList()
    var groupIds: List<String> = emptyList()

    fun requireRootOrgId(): String = rootOrgId
        ?: throw IllegalStateException("rootOrgId not set in RequestContext - JWT filter may not have run")

    fun requireUserId(): String = userId
        ?: throw IllegalStateException("userId not set in RequestContext")

    fun hasRole(role: String): Boolean = roles.contains(role)

    fun isAdmin(): Boolean = hasRole("ADMIN")

    fun isOrgAdmin(): Boolean = hasRole("ORG_ADMIN") || isAdmin()

    fun isOrgManager(orgId: String): Boolean = orgManagerScopes.contains(orgId) || isAdmin()
}
