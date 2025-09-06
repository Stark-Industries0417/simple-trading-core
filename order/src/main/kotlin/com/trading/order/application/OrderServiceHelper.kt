package com.trading.order.application

import com.trading.common.exception.order.OrderPersistenceException
import com.trading.common.exception.order.OrderProcessingException
import com.trading.common.logging.StructuredLogger
import com.trading.order.domain.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class OrderServiceHelper(
    private val structuredLogger: StructuredLogger,
    private val orderMetrics: OrderMetrics
) {
    
    fun handlePersistenceException(
        ex: DataIntegrityViolationException,
        order: Order?,
        userId: String,
        symbol: String,
        startTime: Long
    ): Nothing {
        val duration = System.currentTimeMillis() - startTime
        orderMetrics.incrementDatabaseErrors()
        
        structuredLogger.error("Order persistence failed: constraint violation",
            buildOrderContext(
                order = order,
                userId = userId,
                symbol = symbol,
                duration = duration,
                additionalFields = mapOf(
                    "error" to (ex.message ?: "Unknown error"),
                    "constraintViolation" to true
                )
            )
        )
        
        val exception = OrderPersistenceException("Failed to save order: constraint violation", ex)
            .withContext("userId", userId)
            .withContext("symbol", symbol)
        order?.id?.let { exception.withContext("orderId", it) }
        throw exception
    }
    
    fun handleUnexpectedException(
        ex: Exception,
        order: Order?,
        userId: String,
        symbol: String,
        startTime: Long,
        operation: String
    ): Nothing {
        val duration = System.currentTimeMillis() - startTime
        orderMetrics.incrementUnexpectedErrors()
        
        structuredLogger.error("Unexpected error during $operation",
            buildOrderContext(
                order = order,
                userId = userId,
                symbol = symbol,
                duration = duration,
                additionalFields = mapOf(
                    "error" to (ex.message ?: "Unknown error"),
                    "exceptionType" to ex.javaClass.simpleName
                )
            )
        )
        
        val exception = OrderProcessingException("$operation failed due to unexpected error", ex)
            .withContext("userId", userId)
            .withContext("symbol", symbol)
        order?.id?.let { exception.withContext("orderId", it) }
        throw exception
    }
    
    fun buildOrderContext(
        order: Order? = null,
        orderId: String? = null,
        userId: String? = null,
        symbol: String? = null,
        traceId: String? = null,
        duration: Long? = null,
        additionalFields: Map<String, Any> = emptyMap()
    ): Map<String, Any> = buildMap {
        order?.let {
            put("orderId", it.id)
            put("userId", it.userId)
            put("symbol", it.symbol)
            put("side", it.side.name)
            put("orderType", it.orderType.name)
            put("quantity", it.quantity.toString())
            it.price?.let { price -> put("price", price.toString()) }
            put("status", it.status.name)
            put("version", it.version.toString())
        }
        orderId?.let { put("orderId", it) }
        userId?.let { put("userId", it) }
        symbol?.let { put("symbol", it) }
        traceId?.let { put("traceId", it) }
        duration?.let { put("duration", it.toString()) }
        putAll(additionalFields)
    }
}