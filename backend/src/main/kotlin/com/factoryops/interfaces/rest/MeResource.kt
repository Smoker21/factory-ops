package com.factoryops.interfaces.rest

import com.factoryops.application.service.UserService
import com.factoryops.interfaces.dto.UserResponse
import com.factoryops.interfaces.dto.toResponse
import com.factoryops.interfaces.filter.RequestContext
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Me")
class MeResource {

    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "Get current user profile")
    @APIResponse(responseCode = "200", description = "Current user")
    @APIResponse(responseCode = "401", description = "Unauthorized")
    fun getMe(): Response {
        val userId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val user = userService.getUser(userId, rootOrgId)
        return Response.ok(user.toResponse()).build()
    }

    @GET
    @Path("/notification-preferences")
    @Operation(summary = "Get notification preferences (reserved)")
    fun getNotificationPreferences(): Response {
        return Response.ok(mapOf("emailEnabled" to true, "pushEnabled" to false)).build()
    }

    @PUT
    @Path("/notification-preferences")
    @Operation(summary = "Update notification preferences (reserved)")
    fun updateNotificationPreferences(prefs: Map<String, Any?>): Response {
        return Response.ok(prefs).build()
    }
}
