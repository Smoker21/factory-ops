package com.factoryops.interfaces.rest

import com.factoryops.application.service.GroupService
import com.factoryops.domain.group.GroupMembershipRole
import com.factoryops.interfaces.dto.AddGroupMemberRequest
import com.factoryops.interfaces.dto.CreateGroupRequest
import com.factoryops.interfaces.dto.GroupPageResponse
import com.factoryops.interfaces.dto.GroupResponse
import com.factoryops.interfaces.dto.GroupSettingsResponse
import com.factoryops.interfaces.dto.HistoryEntryResponse
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.TransferGroupLeaderRequest
import com.factoryops.interfaces.dto.UpdateGroupRequest
import com.factoryops.interfaces.dto.UpdateGroupSettingsRequest
import com.factoryops.interfaces.dto.toDomain
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.filter.RequestContext
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

@Path("/v1/orgs/{orgId}/groups")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Groups")
class GroupResource {

    @Inject
    lateinit var groupService: GroupService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List groups")
    fun listGroups(
        @PathParam("orgId") orgId: String,
        @QueryParam("organizationId") organizationId: String?,
        @QueryParam("type") type: String?,
        @QueryParam("underOrgId") underOrgId: String?
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val groups = groupService.listGroups(rootOrgId, organizationId, type, underOrgId)
        return Response.ok(GroupPageResponse(groups.map { it.toResponse() }, PageInfo(null, false))).build()
    }

    @POST
    @Operation(summary = "Create group")
    @APIResponse(responseCode = "201", description = "Group created")
    fun createGroup(@PathParam("orgId") orgId: String, @Valid request: CreateGroupRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val group = groupService.createGroup(rootOrgId, request.organizationId, request.type, request.name, request.code, request.leaderId, request.attributes, actorId)
        return Response.status(201).entity(group.toResponse()).build()
    }

    @GET
    @Path("/{groupId}")
    @Operation(summary = "Get group by ID")
    fun getGroup(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val group = groupService.getGroup(groupId, rootOrgId)
        return Response.ok(group.toResponse()).build()
    }

    @PATCH
    @Path("/{groupId}")
    @Operation(summary = "Update group")
    fun updateGroup(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String, @Valid request: UpdateGroupRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val group = groupService.updateGroup(groupId, rootOrgId, request.name, request.type, request.attributes, actorId)
        return Response.ok(group.toResponse()).build()
    }

    @DELETE
    @Path("/{groupId}")
    @Operation(summary = "Soft-delete group")
    fun deleteGroup(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        groupService.deleteGroup(groupId, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @GET
    @Path("/{groupId}/settings")
    @Operation(summary = "Get group settings including QA settings")
    fun getSettings(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val settings = groupService.getGroupSettings(groupId, rootOrgId)
        return Response.ok(settings.toResponse()).build()
    }

    @PATCH
    @Path("/{groupId}/settings")
    @Operation(summary = "Update group settings")
    fun updateSettings(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String, @Valid request: UpdateGroupSettingsRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val settings = groupService.updateGroupSettings(groupId, rootOrgId, request.qa?.toDomain(), request.extras, actorId)
        return Response.ok(settings.toResponse()).build()
    }

    @GET
    @Path("/{groupId}/members")
    @Operation(summary = "List group members")
    fun listMembers(
        @PathParam("orgId") orgId: String,
        @PathParam("groupId") groupId: String,
        @QueryParam("includeInactive") @DefaultValue("false") includeInactive: Boolean
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val members = groupService.listMembers(groupId, rootOrgId, includeInactive)
        return Response.ok(members.map { it.toResponse() }).build()
    }

    @POST
    @Path("/{groupId}/members")
    @Operation(summary = "Add member to group")
    @APIResponse(responseCode = "201", description = "Member added")
    fun addMember(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String, @Valid request: AddGroupMemberRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val role = runCatching { GroupMembershipRole.valueOf(request.role) }.getOrDefault(GroupMembershipRole.MEMBER)
        val membership = groupService.addMember(groupId, rootOrgId, request.userId, role, actorId)
        return Response.status(201).entity(membership.toResponse()).build()
    }

    @DELETE
    @Path("/{groupId}/members/{userId}")
    @Operation(summary = "Remove member from group")
    fun removeMember(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String, @PathParam("userId") userId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        groupService.removeMember(groupId, rootOrgId, userId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{groupId}/transfer-leader")
    @Tag(name = "GroupMemberships")
    @Operation(summary = "Transfer group leader")
    fun transferLeader(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String, @Valid request: TransferGroupLeaderRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val group = groupService.transferLeader(groupId, rootOrgId, request.newLeaderId, actorId)
        return Response.ok(group.toResponse()).build()
    }

    @GET
    @Path("/{groupId}/history")
    @Operation(summary = "Get group history")
    fun getHistory(@PathParam("orgId") orgId: String, @PathParam("groupId") groupId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val history = groupService.getGroupHistory(groupId, rootOrgId)
        return Response.ok(history.map { HistoryEntryResponse(it.actorId, it.action, it.at.atOffset(java.time.ZoneOffset.UTC), it.payload) }).build()
    }
}
