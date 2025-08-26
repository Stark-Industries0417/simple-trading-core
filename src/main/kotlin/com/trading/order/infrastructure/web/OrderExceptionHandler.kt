package com.trading.order.infrastructure.web

import com.trading.common.exception.*
import com.trading.common.logging.StructuredLogger
import com.trading.order.application.OrderMetrics
import com.trading.order.infrastructure.web.dto.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest




@RestControllerAdvice
class OrderExceptionHandler(
    private val structuredLogger: StructuredLogger,
    private val orderMetrics: OrderMetrics
) {
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val fieldErrors = ex.bindingResult.fieldErrors.associate { error: FieldError ->
            error.field to (error.defaultMessage ?: "Invalid value")
        }
        
        val traceId = MDC.get("traceId")
        
        structuredLogger.warn("Bean validation failed",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("fieldErrors", fieldErrors)
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse.validationError(
            message = "Request validation failed",
            path = request.requestURI,
            fieldErrors = fieldErrors,
            traceId = traceId
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(OrderValidationException::class)
    fun handleOrderValidationException(
        ex: OrderValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        orderMetrics.incrementValidationFailures()
        val traceId = MDC.get("traceId")
        
        val violations = ex.context["validationErrors"] as? List<String> ?: emptyList()
        
        structuredLogger.warn("Business rules validation failed",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                put("violations", violations)
                ex.context["orderId"]?.let { put("orderId", it) }
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse.businessRuleViolation(
            message = ex.message ?: "Business rule validation failed",
            path = request.requestURI,
            violations = violations,
            traceId = traceId
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFoundException(
        ex: OrderNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val traceId = MDC.get("traceId")
        
        structuredLogger.info("Order not found",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                ex.context["orderId"]?.let { put("orderId", it) }
                ex.context["userId"]?.let { put("userId", it) }
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse.notFound(
            message = ex.message ?: "Order not found",
            path = request.requestURI,
            resourceType = "Order",
            resourceId = ex.context["orderId"] as? String,
            traceId = traceId
        )
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse)
    }
    
    @ExceptionHandler(OrderStateException::class)
    fun handleOrderStateException(
        ex: OrderStateException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val traceId = MDC.get("traceId")
        val currentState = ex.context["currentStatus"] as? String
        
        structuredLogger.warn("Invalid order state operation",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                ex.context["orderId"]?.let { put("orderId", it) }
                currentState?.let { put("currentState", it) }
                put("errorCode", ex.errorCode)
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse.invalidState(
            message = ex.message ?: "Invalid order state for requested operation",
            path = request.requestURI,
            currentState = currentState,
            traceId = traceId
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }
    
    @ExceptionHandler(OrderPersistenceException::class)
    fun handleOrderPersistenceException(
        ex: OrderPersistenceException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        orderMetrics.incrementDatabaseErrors()
        val traceId = MDC.get("traceId")
        
        structuredLogger.error("Database operation failed",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                ex.context["orderId"]?.let { put("orderId", it) }
                traceId?.let { put("traceId", it) }
            },
            ex
        )
        
        val errorResponse = ErrorResponse(
            code = "DATABASE_ERROR",
            message = "Unable to process request due to data storage issue",
            path = request.requestURI,
            traceId = traceId
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
    
    @ExceptionHandler(OrderRetrievalException::class)
    fun handleOrderRetrievalException(
        ex: OrderRetrievalException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val traceId = MDC.get("traceId")
        
        structuredLogger.error("Order retrieval failed",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                ex.context["userId"]?.let { put("userId", it) }
                traceId?.let { put("traceId", it) }
            },
            ex
        )
        
        val errorResponse = ErrorResponse(
            code = "RETRIEVAL_ERROR",
            message = "Unable to retrieve order information",
            path = request.requestURI,
            traceId = traceId
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
    
    @ExceptionHandler(OrderProcessingException::class)
    fun handleOrderProcessingException(
        ex: OrderProcessingException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        orderMetrics.incrementUnexpectedErrors()
        val traceId = MDC.get("traceId")
        
        structuredLogger.error("Order processing failed",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                put("context", ex.context)
                traceId?.let { put("traceId", it) }
            },
            ex
        )
        
        val errorResponse = ErrorResponse(
            code = "PROCESSING_ERROR",
            message = "Order processing failed. Please try again.",
            path = request.requestURI,
            traceId = traceId
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
    
    @ExceptionHandler(InsufficientBalanceException::class)
    fun handleInsufficientBalanceException(
        ex: InsufficientBalanceException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val traceId = MDC.get("traceId")
        
        structuredLogger.warn("Insufficient balance for order",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse(
            code = "INSUFFICIENT_BALANCE",
            message = ex.message ?: "Insufficient balance for this operation",
            path = request.requestURI,
            traceId = traceId
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        val traceId = MDC.get("traceId")
        
        structuredLogger.warn("Business exception occurred",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorCode", ex.errorCode)
                put("context", ex.context)
                traceId?.let { put("traceId", it) }
            }
        )
        
        val errorResponse = ErrorResponse(
            code = ex.errorCode,
            message = ex.message ?: "Business rule violation",
            path = request.requestURI,
            traceId = traceId
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        
        orderMetrics.incrementUnexpectedErrors()
        val traceId = MDC.get("traceId")
        
        structuredLogger.error("Unexpected error in order processing",
            buildMap {
                put("path", request.requestURI)
                put("method", request.method)
                put("errorType", ex.javaClass.simpleName)
                traceId?.let { put("traceId", it) }
            },
            ex
        )
        
        val errorResponse = ErrorResponse.internalError(
            path = request.requestURI,
            traceId = traceId,
            includeStackTrace = false // 운영 환경에서는 스택 트레이스 미포함
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}