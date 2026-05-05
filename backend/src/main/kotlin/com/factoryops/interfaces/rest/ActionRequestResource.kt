package com.factoryops.interfaces.rest

import com.factoryops.application.service.DispatchService
import com.factoryops.domain.actionrequest.ActionRequestStatus
import com.factoryops.domain.actionrequest.SeverityLevel
import com.factoryops.interfaces.dto.ActionRequestPageResponse
import com.factoryops.interfaces.dto.ActionRequestResponse
import com.factoryops.interfaces.dto.ChangeActionRequestStatusRequest
import com.factoryops.interfaces.dto.ConvertActionRequestToTaskRequest
import com.factoryops.interfaces.dto.CreateActionRequestRequest
import com.factoryops.interfaces.dto.DispatchActionRequestRequest
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.UpdateActionRequestRequest
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.exception.ValidationException
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

@Path("/v1/action-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ActionRequests")
class ActionRequestResource {

    @Inject
    lateinit var dispatchService: DispatchService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List action requests with optional cursor-based pagination")
    @APIResponse(responseCode = "200", description = "Action request page")
    fun list(
        @QueryParam("status") status: String?,
        @QueryParam("requesterId") requesterId: String?,
        @QueryParam("cursor") cursor: String?,
        @QueryParam("limit") @DefaultValue("20") limit: Int
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val (ars, pageInfo) = dispatchService.listActionRequests(rootOrgId, status, requesterId, cursor, limit)
        return Response.ok(ActionRequestPageResponse(ars.map { it.toResponse() }, pageInfo)).build()
    }

    @POST
    @RolesAllowed("OPERATOR", "SHIFT_LEAD", "ENGINEER", "QA", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Submit action request")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(@Valid request: CreateActionRequestRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val severity = runCatching { SeverityLevel.valueOf(request.severityLevel) }
            .getOrElse { throw ValidationException("Invalid severity level: ${request.severityLevel}", "invalid_severity") }
        val ar = dispatchService.createActionRequest(rootOrgId, request.projectId, request.originatingOrgId, request.targetOrgId, request.title, request.descriptionMarkdown, severity, request.severityReason, actorId)
        return Response.status(201).entity(ar.toResponse()).build()
    }

    @GET
    @Path("/{actionRequestId}")
    @Operation(summary = "Get action request by ID")
    fun getById(@PathParam("actionRequestId") id: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val ar = dispatchService.getActionRequest(id, rootOrgId)
        return Response.ok(ar.toResponse()).build()
    }

    @PATCH
    @Path("/{actionRequestId}")
    @RolesAllowed("OPERATOR", "SHIFT_LEAD", "ENGINEER", "QA", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update action request")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun update(@PathParam("actionRequestId") id: String, @Valid request: UpdateActionRequestRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val ar = dispatchService.updateActionRequest(id, rootOrgId, request.title, request.descriptionMarkdown, actorId)
        return Response.ok(ar.toResponse()).build()
    }

    @DELETE
    @Path("/{actionRequestId}")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Soft-delete action request")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun delete(@PathParam("actionRequestId") id: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        dispatchService.deleteActionRequest(id, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{actionRequestId}/status")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Change action request status (triage)")
    @APIResponse(responseCode = "200", description = "Status changed")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun changeStatus(@PathParam("actionRequestId") id: String, @Valid request: ChangeActionRequestStatusRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val status = runCatching { ActionRequestStatus.valueOf(request.status) }
            .getOrElse { throw ValidationException("Invalid status: ${request.status}", "invalid_status") }
        val ar = dispatchService.changeActionRequestStatus(id, rootOrgId, status, request.reason, actorId)
        return Response.ok(ar.toResponse()).build()
    }

    @POST
    @Path("/{actionRequestId}/convert-to-task")
    @RolesAllowed("SHIFT_LEAD", "GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Convert action request to task (triage)")
    @APIResponse(responseCode = "201", description = "Task created from action request")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun convertToTask(@PathParam("actionRequestId") id: String, @Valid request: ConvertActionRequestToTaskRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val task = dispatchService.convertToTask(id, rootOrgId, request.projectId, request.taskType, request.taskTitle, actorId)
        return Response.status(201).entity(task.toResponse()).build()
    }
}

@Path("/v1/orgs/{orgId}/dispatch-action-request")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ActionRequests")
class DispatchResource {

    @Inject
    lateinit var dispatchService: DispatchService

    @Inject
    lateinit var requestContext: RequestContext

    @POST
    @RolesAllowed("ORG_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Dispatch action request from upper org manager to leaf org (Single-hop; v1.3)")
    @APIResponse(responseCode = "201", description = "ActionRequest dispatched")
    @APIResponse(responseCode = "403", description = "Not authorized to dispatch")
    @APIResponse(responseCode = "409", description = "Target org has no leaders")
    @APIResponse(responseCode = "422", description = "Validation error")
    fun dispatch(@PathParam("orgId") targetOrgId: String, @Valid request: DispatchActionRequestRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val orgManagerScopes = requestContext.orgManagerScopes
        val severity = runCatching { SeverityLevel.valueOf(request.severityLevel) }
            .getOrElse { throw ValidationException("Invalid severity level: ${request.severityLevel}", "invalid_severity") }

        val ar = dispatchService.dispatchActionRequest(
            targetOrgId = targetOrgId,
            title = request.title,
            descriptionMarkdown = request.descriptionMarkdown,
            severityLevel = severity,
            severityReason = request.severityReason,
            ownerId = request.ownerId,
            actorId = actorId,
            actorOrgManagerScopes = orgManagerScopes,
            actorRootOrgId = rootOrgId
        )
        return Response.status(201).entity(ar.toResponse()).build()
    }
}
