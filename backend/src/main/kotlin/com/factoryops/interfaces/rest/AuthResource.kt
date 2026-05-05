package com.factoryops.interfaces.rest

import com.factoryops.application.service.AuthService
import com.factoryops.interfaces.dto.ChangePasswordRequest
import com.factoryops.interfaces.dto.LoginRequest
import com.factoryops.interfaces.dto.LogoutRequest
import com.factoryops.interfaces.dto.RefreshRequest
import com.factoryops.interfaces.dto.TokenPairResponse
import com.factoryops.interfaces.filter.RequestContext
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.validation.Valid
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.openapi.annotations.Operation
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse
import org.eclipse.microprofile.openapi.annotations.tags.Tag

@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Auth", description = "Authentication and token management")
class AuthResource {

    @Inject
    lateinit var authService: AuthService

    @Inject
    lateinit var requestContext: RequestContext

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Login with accountName and password")
    @APIResponse(responseCode = "200", description = "Login successful")
    @APIResponse(responseCode = "401", description = "Invalid credentials")
    @APIResponse(responseCode = "422", description = "Validation error")
    fun login(@Valid request: LoginRequest): Response {
        val tokenPair = authService.login(request.orgCode, request.accountName, request.password)
        return Response.ok(TokenPairResponse(
            accessToken = tokenPair.accessToken,
            refreshToken = tokenPair.refreshToken,
            expiresIn = tokenPair.expiresIn
        )).build()
    }

    @POST
    @Path("/refresh")
    @PermitAll
    @Operation(summary = "Refresh access token using refresh token")
    @APIResponse(responseCode = "200", description = "New token pair")
    @APIResponse(responseCode = "401", description = "Invalid refresh token")
    fun refresh(@Valid request: RefreshRequest): Response {
        val tokenPair = authService.refresh(request.refreshToken)
        return Response.ok(TokenPairResponse(
            accessToken = tokenPair.accessToken,
            refreshToken = tokenPair.refreshToken,
            expiresIn = tokenPair.expiresIn
        )).build()
    }

    @POST
    @Path("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    @APIResponse(responseCode = "204", description = "Logged out")
    fun logout(@Valid request: LogoutRequest): Response {
        authService.logout(request.refreshToken)
        return Response.noContent().build()
    }

    @PUT
    @Path("/password")
    @Operation(summary = "Change own password")
    @APIResponse(responseCode = "204", description = "Password changed")
    @APIResponse(responseCode = "401", description = "Wrong current password")
    fun changePassword(@Valid request: ChangePasswordRequest): Response {
        val userId = requestContext.requireUserId()
        val rootOrgId = requestContext.requireRootOrgId()
        authService.changePassword(userId, rootOrgId, request.currentPassword, request.newPassword)
        return Response.noContent().build()
    }
}
