package com.factoryops.interfaces.rest

import jakarta.annotation.security.PermitAll
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/v1/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health")
class HealthResource {

    @GET
    @PermitAll
    @Operation(summary = "Liveness probe")
    fun health(): Response = Response.ok(mapOf("status" to "UP")).build()
}
