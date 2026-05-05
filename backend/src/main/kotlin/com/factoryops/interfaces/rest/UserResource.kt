package com.factoryops.interfaces.rest

import com.factoryops.application.service.UserService
import com.factoryops.domain.shared.enums.Role
import com.factoryops.interfaces.dto.CreateUserRequest
import com.factoryops.interfaces.dto.UpdateUserRequest
import com.factoryops.interfaces.dto.UserPageResponse
import com.factoryops.interfaces.dto.UserResponse
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.filter.RequestContext
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Users")
class UserResource {

    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List users in root org with optional cursor-based pagination")
    @APIResponse(responseCode = "200", description = "User page")
    fun list(
        @QueryParam("q") q: String?,
        @QueryParam("active") active: Boolean?,
        @QueryParam("cursor") cursor: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val (users, pageInfo) = userService.listUsers(rootOrgId, q, active, cursor, limit)
        return Response.ok(UserPageResponse(users.map { it.toResponse() }, pageInfo)).build()
    }

    @POST
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Create user from HR")
    @APIResponse(responseCode = "201", description = "User created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "409", description = "Conflict")
    @APIResponse(responseCode = "422", description = "Validation error")
    fun create(@Valid request: CreateUserRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val roles = request.roles.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
        val user = userService.createUser(rootOrgId, request.accountName, roles, request.defaultPassword, actorId)
        return Response.status(201).entity(user.toResponse()).build()
    }

    @GET
    @Path("/{userId}")
    @Operation(summary = "Get user by ID")
    @APIResponse(responseCode = "200", description = "User")
    @APIResponse(responseCode = "404", description = "Not found")
    fun getById(@PathParam("userId") userId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val user = userService.getUser(userId, rootOrgId)
        return Response.ok(user.toResponse()).build()
    }

    @PATCH
    @Path("/{userId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update user")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "422", description = "Cannot modify own roles")
    fun update(@PathParam("userId") userId: String, @Valid request: UpdateUserRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val roles = request.roles?.mapNotNull { runCatching { Role.valueOf(it) }.getOrNull() }
        val user = userService.updateUser(userId, rootOrgId, roles, request.active, actorId)
        return Response.ok(user.toResponse()).build()
    }

    @DELETE
    @Path("/{userId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Soft-delete user")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun delete(@PathParam("userId") userId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        userService.deleteUser(userId, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{userId}/sync-from-hr")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Sync user from HR")
    @APIResponse(responseCode = "200", description = "Synced")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun syncFromHr(@PathParam("userId") userId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val user = userService.syncFromHr(userId, rootOrgId)
        return Response.ok(user.toResponse()).build()
    }

    @GET
    @Path("/{userId}/org-manager-scopes")
    @Operation(summary = "Get user's org manager scopes")
    fun getOrgManagerScopes(@PathParam("userId") userId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val scopes = userService.getOrgManagerScopes(userId, rootOrgId)
        return Response.ok(mapOf("orgIds" to scopes)).build()
    }
}
