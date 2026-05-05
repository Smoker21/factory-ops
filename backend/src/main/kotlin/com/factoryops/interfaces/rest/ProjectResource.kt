package com.factoryops.interfaces.rest

import com.factoryops.application.service.ProjectService
import com.factoryops.domain.membership.MembershipRole
import com.factoryops.domain.project.ProjectStatus
import com.factoryops.interfaces.dto.AddProjectMemberRequest
import com.factoryops.interfaces.dto.ChangeProjectOwnerRequest
import com.factoryops.interfaces.dto.ChangeProjectStatusRequest
import com.factoryops.interfaces.dto.CreateProjectRequest
import com.factoryops.interfaces.dto.HistoryEntryResponse
import com.factoryops.interfaces.dto.MembershipResponse
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.ProjectPageResponse
import com.factoryops.interfaces.dto.ProjectResponse
import com.factoryops.interfaces.dto.UpdateProjectRequest
import com.factoryops.interfaces.exception.ValidationException
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
import java.time.ZoneOffset

@Path("/v1/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Projects")
class ProjectResource {

    @Inject
    lateinit var projectService: ProjectService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List projects with optional cursor-based pagination")
    @APIResponse(responseCode = "200", description = "Project page")
    fun list(
        @QueryParam("status") status: String?,
        @QueryParam("ownerId") ownerId: String?,
        @QueryParam("memberId") memberId: String?,
        @QueryParam("groupId") groupId: String?,
        @QueryParam("cursor") cursor: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val (projects, pageInfo) = projectService.listProjects(rootOrgId, status, ownerId, memberId, groupId, cursor, limit)
        return Response.ok(ProjectPageResponse(projects.map { it.toResponse() }, pageInfo)).build()
    }

    @POST
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Create project")
    @APIResponse(responseCode = "201", description = "Project created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(@Valid request: CreateProjectRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val project = projectService.createProject(
            rootOrgId = rootOrgId,
            organizationId = request.organizationId,
            name = request.name,
            descriptionMarkdown = request.descriptionMarkdown,
            ownerId = request.ownerId,
            memberIds = request.memberIds,
            groupIds = request.groupIds,
            schedule = request.schedule?.toDomain(),
            tags = request.tags,
            actorId = actorId
        )
        return Response.status(201).entity(project.toResponse()).build()
    }

    @GET
    @Path("/{projectId}")
    @Operation(summary = "Get project by ID")
    fun getById(@PathParam("projectId") projectId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val project = projectService.getProject(projectId, rootOrgId)
        return Response.ok(project.toResponse()).build()
    }

    @PATCH
    @Path("/{projectId}")
    @RolesAllowed("GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update project")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun update(@PathParam("projectId") projectId: String, @Valid request: UpdateProjectRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val project = projectService.updateProject(projectId, rootOrgId, request.name, request.descriptionMarkdown, request.schedule?.toDomain(), request.tags, actorId)
        return Response.ok(project.toResponse()).build()
    }

    @DELETE
    @Path("/{projectId}")
    @RolesAllowed("GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Soft-delete project")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun delete(@PathParam("projectId") projectId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        projectService.deleteProject(projectId, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{projectId}/status")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Change project status")
    @APIResponse(responseCode = "200", description = "Status changed")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun changeStatus(@PathParam("projectId") projectId: String, @Valid request: ChangeProjectStatusRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val status = runCatching { ProjectStatus.valueOf(request.status) }
            .getOrElse { throw ValidationException("Invalid status: ${request.status}", "invalid_status") }
        val project = projectService.changeProjectStatus(projectId, rootOrgId, status, request.reason, actorId)
        return Response.ok(project.toResponse()).build()
    }

    @POST
    @Path("/{projectId}/owner")
    @RolesAllowed("GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Transfer project owner")
    @APIResponse(responseCode = "200", description = "Owner transferred")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun transferOwner(@PathParam("projectId") projectId: String, @Valid request: ChangeProjectOwnerRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val project = projectService.changeProjectOwner(projectId, rootOrgId, request.newOwnerId, request.reason, actorId)
        return Response.ok(project.toResponse()).build()
    }

    @GET
    @Path("/{projectId}/members")
    @Tag(name = "Memberships")
    @Operation(summary = "List project members")
    fun listMembers(@PathParam("projectId") projectId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val members = projectService.listMembers(projectId, rootOrgId)
        return Response.ok(members.map { it.toResponse() }).build()
    }

    @POST
    @Path("/{projectId}/members")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Tag(name = "Memberships")
    @Operation(summary = "Add project member")
    @APIResponse(responseCode = "201", description = "Member added")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun addMember(@PathParam("projectId") projectId: String, @Valid request: AddProjectMemberRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val role = runCatching { MembershipRole.valueOf(request.role) }.getOrDefault(MembershipRole.MEMBER)
        val membership = projectService.addMember(projectId, rootOrgId, request.userId, role, actorId)
        return Response.status(201).entity(membership.toResponse()).build()
    }

    @DELETE
    @Path("/{projectId}/members/{userId}")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Tag(name = "Memberships")
    @Operation(summary = "Remove project member")
    @APIResponse(responseCode = "204", description = "Removed")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun removeMember(@PathParam("projectId") projectId: String, @PathParam("userId") userId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        projectService.removeMember(projectId, rootOrgId, userId, actorId)
        return Response.noContent().build()
    }

    @GET
    @Path("/{projectId}/history")
    @Operation(summary = "Get project history")
    fun getHistory(@PathParam("projectId") projectId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val history = projectService.getProjectHistory(projectId, rootOrgId)
        return Response.ok(history.map { HistoryEntryResponse(it.actorId, it.action, it.at.atOffset(ZoneOffset.UTC), it.payload) }).build()
    }
}
