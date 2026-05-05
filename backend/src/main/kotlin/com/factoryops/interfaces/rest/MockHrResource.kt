package com.factoryops.interfaces.rest

import com.factoryops.infrastructure.hr.HrClient
import com.factoryops.infrastructure.hr.HrEmployee
import io.quarkus.arc.profile.IfBuildProfile
import jakarta.annotation.security.PermitAll
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.tags.Tag

/**
 * HR introspection endpoints for development only (ADR-0007 / ADR-0014).
 * Excluded from prod builds via @IfBuildProfile("dev").
 *
 * Delegates to whichever [HrClient] the runtime selected via `hr.mode`
 * (mock or h2), so the same URLs work regardless of which backend is active.
 */
@IfBuildProfile("dev")
@Path("/mock-hr")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "MockHR")
class MockHrResource {

    @Inject
    lateinit var hrClient: HrClient

    @GET
    @Path("/users/{accountName}")
    @PermitAll
    @Operation(summary = "Get HR employee by accountName (Mock)")
    fun getByAccountName(@PathParam("accountName") accountName: String): Response {
        val employee = hrClient.findByAccountName(accountName)
            ?: return Response.status(404).entity(mapOf("message" to "Employee not found: $accountName")).build()
        return Response.ok(employee.toResponse()).build()
    }

    @GET
    @Path("/users")
    @PermitAll
    @Operation(summary = "List all mock HR employees")
    fun listAll(): Response {
        val employees = listOf(
            "admin.system",
            "manager.wang",
            "leader.chen",
            "operator.li",
            "qa.zhang"
        ).mapNotNull { hrClient.findByAccountName(it)?.toResponse() }
        return Response.ok(employees).build()
    }

    private fun HrEmployee.toResponse() = mapOf(
        "accountName" to accountName,
        "employeeNo" to employeeNo,
        "displayName" to displayName,
        "email" to email,
        "active" to active,
        "defaultRoles" to defaultRoles
    )
}
