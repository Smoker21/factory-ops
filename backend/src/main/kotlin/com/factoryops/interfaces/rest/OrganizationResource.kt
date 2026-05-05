package com.factoryops.interfaces.rest

import com.factoryops.application.service.OrganizationService
import com.factoryops.interfaces.dto.AddLeaderRequest
import com.factoryops.interfaces.dto.CreateOrganizationRequest
import com.factoryops.interfaces.dto.HistoryEntryResponse
import com.factoryops.interfaces.dto.OrganizationPageResponse
import com.factoryops.interfaces.dto.OrganizationResponse
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.TransferManagerRequest
import com.factoryops.interfaces.dto.UpdateOrganizationRequest
import com.factoryops.interfaces.dto.UserResponse
import com.factoryops.interfaces.dto.toDomain
import com.factoryops.interfaces.dto.toOrganizationLeaderResponse
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.filter.RequestContext
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/v1/orgs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Organizations")
class OrganizationResource {

    @Inject
    lateinit var orgService: OrganizationService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List organizations with optional cursor-based pagination")
    @APIResponse(responseCode = "200", description = "Organization list")
    fun list(
        @QueryParam("parentId") parentId: String?,
        @QueryParam("type") type: String?,
        @QueryParam("leafOnly") @DefaultValue("false") leafOnly: Boolean,
        @QueryParam("underOrgId") underOrgId: String?,
        @QueryParam("cursor") cursor: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val (orgs, pageInfo) = orgService.listOrgs(rootOrgId, parentId, type, leafOnly, underOrgId, cursor, limit)
        return Response.ok(OrganizationPageResponse(orgs.map { it.toResponse() }, pageInfo)).build()
    }

    @POST
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Create organization node")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "409", description = "Conflict")
    fun create(@Valid request: CreateOrganizationRequest): Response {
        val actorId = requestContext.requireUserId()
        val actorRootOrgId = requestContext.rootOrgId

        val org = orgService.createOrg(
            type = request.type,
            name = request.name,
            code = request.code,
            parentId = request.parentId,
            managerId = request.managerId,
            leaderIds = request.leaderIds,
            timezone = request.timezone,
            locale = request.locale,
            settings = request.settings?.toDomain(),
            actorId = actorId,
            actorRootOrgId = actorRootOrgId
        )
        return Response.status(201).entity(org.toResponse()).build()
    }

    @GET
    @Path("/{orgId}")
    @Operation(summary = "Get single organization")
    @APIResponse(responseCode = "200", description = "Organization")
    @APIResponse(responseCode = "404", description = "Not found")
    fun getById(@PathParam("orgId") orgId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val org = orgService.getOrg(orgId, rootOrgId)
        return Response.ok(org.toResponse()).build()
    }

    @PATCH
    @Path("/{orgId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update organization")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun update(@PathParam("orgId") orgId: String, @Valid request: UpdateOrganizationRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val org = orgService.updateOrg(orgId, rootOrgId, request.name, request.parentId, request.type, request.timezone, request.locale, request.settings?.toDomain(), actorId)
        return Response.ok(org.toResponse()).build()
    }

    @DELETE
    @Path("/{orgId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Soft-delete organization")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun delete(@PathParam("orgId") orgId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        orgService.deleteOrg(orgId, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{orgId}/transfer-manager")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Transfer organization manager")
    @APIResponse(responseCode = "200", description = "Manager updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun transferManager(@PathParam("orgId") orgId: String, @Valid request: TransferManagerRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val org = orgService.transferManager(orgId, rootOrgId, request.newManagerId, actorId)
        return Response.ok(org.toResponse()).build()
    }

    @GET
    @Path("/{orgId}/leaders")
    @Operation(summary = "List organization leaders")
    @APIResponse(responseCode = "200", description = "Leader list")
    fun listLeaders(@PathParam("orgId") orgId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val leaders = orgService.getLeaders(orgId, rootOrgId)
        return Response.ok(leaders.map { it.toOrganizationLeaderResponse() }).build()
    }

    @POST
    @Path("/{orgId}/leaders")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Add organization leader")
    @APIResponse(responseCode = "201", description = "Leader added")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun addLeader(@PathParam("orgId") orgId: String, @Valid request: AddLeaderRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val org = orgService.addLeader(orgId, rootOrgId, request.userId, actorId)
        return Response.status(201).entity(org.toResponse()).build()
    }

    @DELETE
    @Path("/{orgId}/leaders/{userId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Remove organization leader")
    @APIResponse(responseCode = "204", description = "Leader removed")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun removeLeader(@PathParam("orgId") orgId: String, @PathParam("userId") userId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        orgService.removeLeader(orgId, rootOrgId, userId, actorId)
        return Response.noContent().build()
    }
}
