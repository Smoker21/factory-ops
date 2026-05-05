package com.factoryops.interfaces.exception

import jakarta.validation.ConstraintViolationException
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.UriInfo
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider
import mu.KotlinLogging
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

private val logger = KotlinLogging.logger {}

data class ProblemDetail(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val traceId: String? = null,
    val errors: List<FieldError>? = null
)

data class FieldError(
    val field: String?,
    val code: String,
    val message: String
)

@Provider
class GlobalExceptionMapper {

    @ServerExceptionMapper
    fun handleDomainException(ex: DomainException, uriInfo: UriInfo): Response {
        val (status, title) = when (ex) {
            is NotFoundException -> Pair(404, "Not Found")
            is ConflictException -> Pair(409, "Conflict")
            is ValidationException -> Pair(422, "Validation Error")
            is ForbiddenException -> Pair(403, "Forbidden")
            is UnauthorizedException -> Pair(401, "Unauthorized")
            is BusinessRuleViolationException -> Pair(422, "Business Rule Violation")
            is ExternalServiceException -> Pair(503, "External Service Unavailable")
            is StateTransitionException -> Pair(409, "Invalid State Transition")
        }

        logger.warn { "Domain exception [${ex.errorCode}]: ${ex.message}" }

        val problem = ProblemDetail(
            type = "https://factory-ops.example.com/errors/${ex.errorCode}",
            title = title,
            status = status,
            detail = ex.message,
            instance = uriInfo.requestUri.toString(),
            errors = if (ex is ValidationException && ex.field != null) {
                listOf(FieldError(ex.field, ex.errorCode, ex.message ?: ""))
            } else null
        )

        return Response.status(status)
            .type("application/problem+json")
            .entity(problem)
            .build()
    }

    @ServerExceptionMapper
    fun handleConstraintViolation(ex: ConstraintViolationException, uriInfo: UriInfo): Response {
        logger.warn { "Validation constraint violation: ${ex.message}" }

        val errors = ex.constraintViolations.map { cv ->
            FieldError(
                field = cv.propertyPath.toString().substringAfterLast("."),
                code = "constraint_violation",
                message = cv.message
            )
        }

        val problem = ProblemDetail(
            type = "https://factory-ops.example.com/errors/validation_error",
            title = "Validation Error",
            status = 422,
            detail = "Request validation failed",
            instance = uriInfo.requestUri.toString(),
            errors = errors
        )

        return Response.status(422)
            .type("application/problem+json")
            .entity(problem)
            .build()
    }

    @ServerExceptionMapper
    fun handleGenericException(ex: Exception, uriInfo: UriInfo): Response {
        logger.error(ex) { "Unhandled exception at ${uriInfo.requestUri}" }

        val problem = ProblemDetail(
            type = "https://factory-ops.example.com/errors/internal_server_error",
            title = "Internal Server Error",
            status = 500,
            detail = "An unexpected error occurred",
            instance = uriInfo.requestUri.toString()
        )

        return Response.status(500)
            .type("application/problem+json")
            .entity(problem)
            .build()
    }
}
