package com.factoryops.interfaces.rest

import com.factoryops.application.service.TemplateService
import com.factoryops.interfaces.dto.PageInfo
import com.factoryops.interfaces.filter.RequestContext
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
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

@Path("/v1/system/project-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SystemTemplates")
class SystemProjectTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @PermitAll
    @Operation(summary = "List global project templates")
    fun list(@QueryParam("active") active: Boolean?): Response {
        val templates = templateService.listGlobalProjectTemplates()
        return Response.ok(mapOf("items" to templates, "pageInfo" to PageInfo(null, false))).build()
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Create global project template (ADMIN)")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(body: Map<String, Any?>): Response {
        val actorId = requestContext.requireUserId()
        val template = templateService.createProjectTemplate(null, body, actorId)
        return Response.status(201).entity(template).build()
    }

    @GET
    @Path("/{templateId}")
    @PermitAll
    @Operation(summary = "Get global project template")
    fun getById(@PathParam("templateId") templateId: String): Response {
        val template = templateService.getProjectTemplate(templateId)
        return Response.ok(template).build()
    }

    @PATCH
    @Path("/{templateId}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Update global project template (ADMIN)")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "422", description = "Template inactive or version conflict")
    fun update(@PathParam("templateId") templateId: String, body: Map<String, Any?>): Response {
        val template = templateService.updateProjectTemplate(templateId, body)
        return Response.ok(template).build()
    }

    @DELETE
    @Path("/{templateId}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Deactivate global project template (ADMIN)")
    @APIResponse(responseCode = "204", description = "Deactivated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun deactivate(@PathParam("templateId") templateId: String): Response {
        templateService.deactivateProjectTemplate(templateId)
        return Response.noContent().build()
    }
}

@Path("/v1/system/task-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "SystemTemplates")
class SystemTaskTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @PermitAll
    @Operation(summary = "List global task templates")
    fun list(@QueryParam("type") type: String?, @QueryParam("active") active: Boolean?): Response {
        val templates = templateService.listGlobalTaskTemplates()
        return Response.ok(mapOf("items" to templates, "pageInfo" to PageInfo(null, false))).build()
    }

    @POST
    @RolesAllowed("ADMIN")
    @Operation(summary = "Create global task template (ADMIN)")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(body: Map<String, Any?>): Response {
        val actorId = requestContext.requireUserId()
        val template = templateService.createTaskTemplate(null, body, actorId)
        return Response.status(201).entity(template).build()
    }

    @GET
    @Path("/{templateId}")
    @PermitAll
    @Operation(summary = "Get global task template")
    fun getById(@PathParam("templateId") templateId: String): Response {
        val template = templateService.getTaskTemplate(templateId)
        return Response.ok(template).build()
    }

    @PATCH
    @Path("/{templateId}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Update global task template (ADMIN)")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "422", description = "Template inactive")
    fun update(@PathParam("templateId") templateId: String, body: Map<String, Any?>): Response {
        val template = templateService.updateTaskTemplate(templateId, body)
        return Response.ok(template).build()
    }

    @DELETE
    @Path("/{templateId}")
    @RolesAllowed("ADMIN")
    @Operation(summary = "Deactivate global task template (ADMIN)")
    @APIResponse(responseCode = "204", description = "Deactivated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun deactivate(@PathParam("templateId") templateId: String): Response {
        templateService.deactivateTaskTemplate(templateId)
        return Response.noContent().build()
    }
}

@Path("/v1/orgs/{orgId}/project-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ProjectTemplates")
class OrgProjectTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List org project templates")
    fun list(
        @PathParam("orgId") orgId: String,
        @QueryParam("includeGlobal") @DefaultValue("false") includeGlobal: Boolean
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val templates = templateService.listOrgProjectTemplates(rootOrgId, includeGlobal)
        return Response.ok(mapOf("items" to templates, "pageInfo" to PageInfo(null, false))).build()
    }

    @POST
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Create org project template")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(@PathParam("orgId") orgId: String, body: Map<String, Any?>): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val template = templateService.createProjectTemplate(rootOrgId, body, actorId)
        return Response.status(201).entity(template).build()
    }

    @GET
    @Path("/{templateId}")
    @Operation(summary = "Get org project template")
    fun getById(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String): Response {
        val template = templateService.getProjectTemplate(templateId)
        return Response.ok(template).build()
    }

    @PATCH
    @Path("/{templateId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update org project template (increments version)")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "422", description = "Template inactive")
    fun update(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String, body: Map<String, Any?>): Response {
        val template = templateService.updateProjectTemplate(templateId, body)
        return Response.ok(template).build()
    }

    @DELETE
    @Path("/{templateId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Deactivate org project template")
    @APIResponse(responseCode = "204", description = "Deactivated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun deactivate(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String): Response {
        templateService.deactivateProjectTemplate(templateId)
        return Response.noContent().build()
    }
}

@Path("/v1/orgs/{orgId}/task-templates")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "TaskTemplates")
class OrgTaskTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @Operation(summary = "List org task templates")
    fun list(
        @PathParam("orgId") orgId: String,
        @QueryParam("type") type: String?,
        @QueryParam("includeGlobal") @DefaultValue("false") includeGlobal: Boolean
    ): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val templates = templateService.listOrgTaskTemplates(rootOrgId, includeGlobal)
        return Response.ok(mapOf("items" to templates, "pageInfo" to PageInfo(null, false))).build()
    }

    @POST
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Create org task template")
    @APIResponse(responseCode = "201", description = "Created")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(@PathParam("orgId") orgId: String, body: Map<String, Any?>): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val template = templateService.createTaskTemplate(rootOrgId, body, actorId)
        return Response.status(201).entity(template).build()
    }

    @GET
    @Path("/{templateId}")
    @Operation(summary = "Get org task template")
    fun getById(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String): Response {
        val template = templateService.getTaskTemplate(templateId)
        return Response.ok(template).build()
    }

    @PATCH
    @Path("/{templateId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Update org task template (increments version)")
    @APIResponse(responseCode = "200", description = "Updated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    @APIResponse(responseCode = "422", description = "Template inactive")
    fun update(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String, body: Map<String, Any?>): Response {
        val template = templateService.updateTaskTemplate(templateId, body)
        return Response.ok(template).build()
    }

    @DELETE
    @Path("/{templateId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Deactivate org task template")
    @APIResponse(responseCode = "204", description = "Deactivated")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun deactivate(@PathParam("orgId") orgId: String, @PathParam("templateId") templateId: String): Response {
        templateService.deactivateTaskTemplate(templateId)
        return Response.noContent().build()
    }
}

@Path("/v1/orgs/{orgId}/project-templates/fork-from-global")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ProjectTemplates")
class ForkProjectTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @POST
    @Path("/{globalTplId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Fork global project template to org scope")
    @APIResponse(responseCode = "201", description = "Forked")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun fork(@PathParam("orgId") orgId: String, @PathParam("globalTplId") globalTplId: String, body: Map<String, Any?>?): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val template = templateService.forkProjectTemplate(globalTplId, rootOrgId, body?.get("code") as? String, body?.get("name") as? String, actorId)
        return Response.status(201).entity(template).build()
    }
}

@Path("/v1/orgs/{orgId}/task-templates/fork-from-global")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "TaskTemplates")
class ForkTaskTemplateResource {

    @Inject
    lateinit var templateService: TemplateService

    @Inject
    lateinit var requestContext: RequestContext

    @POST
    @Path("/{globalTplId}")
    @RolesAllowed("GROUP_ADMIN", "GROUP_MANAGER", "ORG_ADMIN", "ADMIN")
    @Operation(summary = "Fork global task template to org scope")
    @APIResponse(responseCode = "201", description = "Forked")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun fork(@PathParam("orgId") orgId: String, @PathParam("globalTplId") globalTplId: String, body: Map<String, Any?>?): Response {
        val actorId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        val template = templateService.forkTaskTemplate(globalTplId, rootOrgId, body?.get("code") as? String, body?.get("name") as? String, actorId)
        return Response.status(201).entity(template).build()
    }
}
