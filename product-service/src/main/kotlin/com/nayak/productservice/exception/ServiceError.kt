package com.nayak.productservice.exception

// Ensure this sealed class is defined as previously discussed
sealed class ServiceError {
    data class NotFound(
        val entity: String,
        val id: String,
        override val message: String = "$entity not found with id: $id"
    ) : ServiceError()

    data class Validation(
        val field: String,
        val reason: String,
        override val message: String = "Validation error for field '$field': $reason"
    ) : ServiceError()

    data class Database(
        val operation: String,
        val cause: String,
        override val message: String = "Database error during $operation: $cause"
    ) : ServiceError()

    data class ExternalService(
        val service: String,
        val operation: String,
        val cause: String,
        override val message: String = "Error calling $service during $operation: $cause"
    ) : ServiceError()

    data class BusinessRule(
        val rule: String,
        val details: String,
        override val message: String = "Business rule violation: $rule - $details"
    ) : ServiceError()

    data class Unknown(
        val cause: String,
        override val message: String = "Unknown error: $cause"
    ) : ServiceError()

    abstract val message: String
}