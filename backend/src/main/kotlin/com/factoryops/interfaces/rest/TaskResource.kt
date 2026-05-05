package com.factoryops.interfaces.rest

import com.factoryops.application.service.TaskService
import com.factoryops.domain.shared.enums.Priority
import com.factoryops.domain.shared.enums.Role
import com.factoryops.domain.task.ReviewDecision
import com.factoryops.domain.task.TaskStatus
import com.factoryops.interfaces.dto.AddAssigneesRequest
import com.factoryops.interfaces.dto.ChangeTaskStatusRequest
import com.factoryops.interfaces.dto.CommentResponse
import com.factoryops.interfaces.dto.CreateCommentRequest
import com.factoryops.interfaces.dto.CreateTaskRequest
import com.factoryops.interfaces.dto.EditCommentRequest
import com.factoryops.interfaces.dto.HistoryEntryResponse
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.dto.TaskPageResponse
import com.factoryops.interfaces.dto.TaskReviewRequest
import com.factoryops.interfaces.dto.TaskTypeDefinition
import com.factoryops.interfaces.dto.TransferTaskOwnerRequest
import com.factoryops.interfaces.dto.UpdateTaskRequest
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.exception.ValidationException
import com.factoryops.interfaces.filter.RequestContext
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
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

@Path("/v1/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Tasks")
class TaskResource {

    @Inject
    lateinit var taskService: TaskService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List tasks")
    fun list(
        @QueryParam("projectId") projectId: String?,
        @QueryParam("status") status: String?,
        @QueryParam("ownerId") ownerId: String?,
        @QueryParam("assigneeId") assigneeId: String?,
        @QueryParam("type") type: String?
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val tasks = taskService.listTasks(rootOrgId, projectId, status, ownerId, assigneeId, type)
        return Response.ok(TaskPageResponse(tasks.map { it.toResponse() }, PageInfo(null, false))).build()
    }

    @POST
    @Operation(summary = "Create task")
    @APIResponse(responseCode = "201", description = "Task created")
    fun create(@Valid request: CreateTaskRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val priority = runCatching { Priority.valueOf(request.priority) }.getOrDefault(Priority.NORMAL)
        val task = taskService.createTask(
            rootOrgId = rootOrgId,
            projectId = request.projectId,
            type = request.type,
            title = request.title,
            descriptionMarkdown = request.descriptionMarkdown,
            priority = priority,
            ownerId = request.ownerId,
            assignees = request.assignees,
            attributes = request.attributes,
            schedule = request.schedule?.toDomain(),
            tags = request.tags,
            actorId = actorId
        )
        return Response.status(201).entity(task.toResponse()).build()
    }

    @GET
    @Path("/{taskId}")
    @Operation(summary = "Get task by ID")
    fun getById(@PathParam("taskId") taskId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val task = taskService.getTask(taskId, rootOrgId)
        return Response.ok(task.toResponse()).build()
    }

    @PATCH
    @Path("/{taskId}")
    @Operation(summary = "Update task")
    fun update(@PathParam("taskId") taskId: String, @Valid request: UpdateTaskRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val priority = request.priority?.let { runCatching { Priority.valueOf(it) }.getOrDefault(Priority.NORMAL) }
        val task = taskService.updateTask(taskId, rootOrgId, request.title, request.descriptionMarkdown, priority, request.attributes, request.schedule?.toDomain(), request.tags, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @DELETE
    @Path("/{taskId}")
    @Operation(summary = "Soft-delete task")
    fun delete(@PathParam("taskId") taskId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        taskService.deleteTask(taskId, rootOrgId, actorId)
        return Response.noContent().build()
    }

    @POST
    @Path("/{taskId}/status")
    @Operation(summary = "Change task status")
    fun changeStatus(@PathParam("taskId") taskId: String, @Valid request: ChangeTaskStatusRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val roles = requestContext.roles
        val newStatus = runCatching { TaskStatus.valueOf(request.status) }
            .getOrElse { throw ValidationException("Invalid status: ${request.status}", "invalid_status") }
        val task = taskService.changeTaskStatus(taskId, rootOrgId, newStatus, request.reason, roles, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @POST
    @Path("/{taskId}/assignees")
    @Operation(summary = "Add assignees to task")
    fun addAssignees(@PathParam("taskId") taskId: String, @Valid request: AddAssigneesRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val task = taskService.addAssignees(taskId, rootOrgId, request.userIds, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @DELETE
    @Path("/{taskId}/assignees/{userId}")
    @Operation(summary = "Remove assignee from task")
    fun removeAssignee(@PathParam("taskId") taskId: String, @PathParam("userId") userId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val task = taskService.removeAssignee(taskId, rootOrgId, userId, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @POST
    @Path("/{taskId}/owner")
    @Operation(summary = "Transfer task owner")
    fun transferOwner(@PathParam("taskId") taskId: String, @Valid request: TransferTaskOwnerRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val task = taskService.transferTaskOwner(taskId, rootOrgId, request.newOwnerId, request.keepPreviousOwnerAsAssignee, request.reason, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @POST
    @Path("/{taskId}/review")
    @Operation(summary = "Submit QA review for task")
    fun submitReview(@PathParam("taskId") taskId: String, @Valid request: TaskReviewRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val role = runCatching { Role.valueOf(request.role) }
            .getOrElse { throw ValidationException("Invalid role: ${request.role}", "invalid_role") }
        val decision = runCatching { ReviewDecision.valueOf(request.decision) }
            .getOrElse { throw ValidationException("Invalid decision: ${request.decision}", "invalid_decision") }
        val task = taskService.submitReview(taskId, rootOrgId, role, decision, request.reason, actorId)
        return Response.ok(task.toResponse()).build()
    }

    @GET
    @Path("/{taskId}/comments")
    @Tag(name = "Comments")
    @Operation(summary = "List task comments")
    fun listComments(@PathParam("taskId") taskId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val comments = taskService.listComments(taskId, rootOrgId)
        return Response.ok(comments.map { it.toResponse() }).build()
    }

    @POST
    @Path("/{taskId}/comments")
    @Tag(name = "Comments")
    @Operation(summary = "Add comment to task")
    @APIResponse(responseCode = "201", description = "Comment added")
    fun addComment(@PathParam("taskId") taskId: String, @Valid request: CreateCommentRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val comment = taskService.addComment(taskId, rootOrgId, request.bodyMarkdown, actorId)
        return Response.status(201).entity(comment.toResponse()).build()
    }

    @PATCH
    @Path("/{taskId}/comments/{commentId}")
    @Tag(name = "Comments")
    @Operation(summary = "Edit comment")
    fun editComment(@PathParam("taskId") taskId: String, @PathParam("commentId") commentId: String, @Valid request: EditCommentRequest): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val comment = taskService.editComment(taskId, rootOrgId, commentId, request.bodyMarkdown, actorId)
        return Response.ok(comment.toResponse()).build()
    }

    @DELETE
    @Path("/{taskId}/comments/{commentId}")
    @Tag(name = "Comments")
    @Operation(summary = "Soft-delete comment")
    fun deleteComment(@PathParam("taskId") taskId: String, @PathParam("commentId") commentId: String): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        taskService.deleteComment(taskId, rootOrgId, commentId, actorId)
        return Response.noContent().build()
    }

    @GET
    @Path("/{taskId}/history")
    @Operation(summary = "Get task history")
    fun getHistory(@PathParam("taskId") taskId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val history = taskService.getTaskHistory(taskId, rootOrgId)
        return Response.ok(history.map { HistoryEntryResponse(it.actorId, it.action, it.at.atOffset(ZoneOffset.UTC), it.payload) }).build()
    }
}

@Path("/v1/task-types")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Tasks")
class TaskTypeResource {

    @GET
    @Operation(summary = "List supported task types with attribute JSON schemas")
    fun listTaskTypes(): Response {
        val types = listOf(
            TaskTypeDefinition("EQUIPMENT_INSPECTION", "Equipment inspection task", mapOf("equipmentId" to "string", "checklist" to "array")),
            TaskTypeDefinition("INCIDENT_RESPONSE", "Incident response task", mapOf("incidentType" to "string", "location" to "string", "severity" to "string")),
            TaskTypeDefinition("SHIFT_HANDOVER", "Shift handover task", mapOf("outgoingShiftId" to "string", "incomingShiftId" to "string"))
        )
        return Response.ok(types).build()
    }
}
