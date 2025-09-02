package com.trading.order.infrastructure.web.dto

import java.time.Instant


data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val path: String,
    val details: Map<String, Any>? = null,
    val traceId: String? = null
) {
    companion object {
        fun validationError(
            message: String,
            path: String,
            fieldErrors: Map<String, String> = emptyMap(),
            traceId: String? = null
        ): ErrorResponse {
            val details = if (fieldErrors.isNotEmpty()) {
                mapOf("fieldErrors" to fieldErrors)
            } else null
            
            return ErrorResponse(
                code = "VALIDATION_FAILED",
                message = message,
                path = path,
                details = details,
                traceId = traceId
            )
        }
        
        fun businessRuleViolation(
            message: String,
            path: String,
            violations: List<String> = emptyList(),
            traceId: String? = null
        ): ErrorResponse {
            val details = if (violations.isNotEmpty()) {
                mapOf("violations" to violations)
            } else null
            
            return ErrorResponse(
                code = "BUSINESS_RULE_VIOLATION",
                message = message,
                path = path,
                details = details,
                traceId = traceId
            )
        }
        
        fun notFound(
            message: String,
            path: String,
            resourceType: String? = null,
            resourceId: String? = null,
            traceId: String? = null
        ): ErrorResponse {
            val details = mutableMapOf<String, Any>()
            resourceType?.let { details["resourceType"] = it }
            resourceId?.let { details["resourceId"] = it }
            
            return ErrorResponse(
                code = "RESOURCE_NOT_FOUND",
                message = message,
                path = path,
                details = details.ifEmpty { null },
                traceId = traceId
            )
        }
        
        fun invalidState(
            message: String,
            path: String,
            currentState: String? = null,
            expectedStates: List<String> = emptyList(),
            traceId: String? = null
        ): ErrorResponse {
            val details = mutableMapOf<String, Any>()
            currentState?.let { details["currentState"] = it }
            if (expectedStates.isNotEmpty()) {
                details["expectedStates"] = expectedStates
            }
            
            return ErrorResponse(
                code = "INVALID_STATE",
                message = message,
                path = path,
                details = details.ifEmpty { null },
                traceId = traceId
            )
        }
        
        fun internalError(
            path: String,
            traceId: String? = null,
            includeStackTrace: Boolean = false,
            exception: Exception? = null
        ): ErrorResponse {
            val details = if (includeStackTrace && exception != null) {
                mapOf(
                    "exceptionType" to exception.javaClass.simpleName,
                    "exceptionMessage" to (exception.message ?: "Unknown error")
                )
            } else null
            
            return ErrorResponse(
                code = "INTERNAL_ERROR",
                message = "An unexpected error occurred. Please try again later.",
                path = path,
                details = details,
                traceId = traceId
            )
        }
    }
}