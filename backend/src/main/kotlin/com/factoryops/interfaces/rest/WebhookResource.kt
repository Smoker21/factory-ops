package com.factoryops.interfaces.rest

import com.factoryops.interfaces.filter.RequestContext
import com.factoryops.persistence.document.WebhookDocument
import com.factoryops.persistence.repository.WebhookDeadLetterRepository
import com.factoryops.persistence.repository.WebhookRepository
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.bson.types.ObjectId
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag
import java.time.Instant

data class CreateWebhookRequest(
    @field:NotBlank val targetUrl: String = "",
    val events: List<String> = emptyList(),
    @field:NotBlank val secret: String = ""
)

@Path("/v1/webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Webhooks")
class WebhookResource {

    @Inject
    lateinit var webhookRepository: WebhookRepository

    @Inject
    lateinit var deadLetterRepository: WebhookDeadLetterRepository

    @Inject
    lateinit var requestContext: RequestContext

    @GET
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "List registered webhooks")
    @APIResponse(responseCode = "200", description = "Webhook list")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun list(): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val webhooks = webhookRepository.findByRootOrgId(ObjectId(rootOrgId))
        return Response.ok(webhooks.map { it.toResponse() }).build()
    }

    @POST
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Register webhook")
    @APIResponse(responseCode = "201", description = "Webhook registered")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun create(@Valid request: CreateWebhookRequest): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val now = Instant.now()
        val doc = WebhookDocument().also { d ->
            d.rootOrgId = ObjectId(rootOrgId)
            d.targetUrl = request.targetUrl
            d.events = request.events
            d.secret = request.secret
            d.active = true
            d.createdAt = now
            d.updatedAt = now
        }
        webhookRepository.persist(doc)
        return Response.status(201).entity(doc.toResponse()).build()
    }

    @DELETE
    @Path("/{webhookId}")
    @RolesAllowed("ORG_ADMIN", "ADMIN")
    @Operation(summary = "Remove webhook")
    @APIResponse(responseCode = "204", description = "Removed")
    @APIResponse(responseCode = "403", description = "Forbidden")
    fun delete(@PathParam("webhookId") webhookId: String): Response {
        val rootOrgId = requestContext.requireRootOrgId()
        val doc = webhookRepository.findById(ObjectId(webhookId))
            ?: return Response.status(404).build()

        doc.deletedAt = Instant.now()
        doc.active = false
        webhookRepository.update(doc)
        return Response.noContent().build()
    }

    private fun WebhookDocument.toResponse() = mapOf(
        "id" to id!!.toHexString(),
        "rootOrgId" to rootOrgId.toHexString(),
        "targetUrl" to targetUrl,
        "events" to events,
        "active" to active,
        "createdAt" to createdAt.toString()
        // NOTE: secret is intentionally excluded from API responses
    )
}
