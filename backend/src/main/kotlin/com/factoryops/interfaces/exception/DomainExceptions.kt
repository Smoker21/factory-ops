package com.factoryops.interfaces.exception

/**
 * Domain exception sealed class hierarchy.
 * All domain errors map to RFC 7807 Problem Details via GlobalExceptionMapper.
 */
sealed class DomainException(message: String, val errorCode: String) : RuntimeException(message)

class NotFoundException(message: String, errorCode: String = "not_found") :
    DomainException(message, errorCode)

class ConflictException(message: String, errorCode: String = "conflict") :
    DomainException(message, errorCode)

class ValidationException(message: String, errorCode: String = "validation_error", val field: String? = null) :
    DomainException(message, errorCode)

class ForbiddenException(message: String, errorCode: String = "forbidden") :
    DomainException(message, errorCode)

class UnauthorizedException(message: String, errorCode: String = "unauthorized") :
    DomainException(message, errorCode)

class BusinessRuleViolationException(message: String, errorCode: String = "business_rule_violation") :
    DomainException(message, errorCode)

class ExternalServiceException(message: String, errorCode: String = "external_service_unavailable") :
    DomainException(message, errorCode)

class StateTransitionException(message: String, errorCode: String = "invalid_state_transition") :
    DomainException(message, errorCode)
