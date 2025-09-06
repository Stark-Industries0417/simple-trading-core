package com.trading.order.application

import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.exception.order.OrderNotFoundException
import com.trading.common.exception.order.OrderPersistenceException
import com.trading.common.exception.order.OrderProcessingException
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.*
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class OrderService(
    private val orderRepository: OrderRepository,
    private val orderValidator: OrderValidator,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    private val orderMetrics: OrderMetrics,
    private val outboxRepository: OrderOutboxRepository,
    private val objectMapper: ObjectMapper
) {

    
    private fun handlePersistenceException(
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
    
    private fun handleUnexpectedException(
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
    
    fun createOrder(request: CreateOrderRequest, userId: String, traceId: String): OrderResponse {
        val startTime = System.currentTimeMillis()
        var order: Order? = null
        
        try {
            structuredLogger.info("Order creation started", buildMap {
                put("userId", userId)
                put("symbol", request.symbol)
                put("orderType", request.orderType.name)
                put("side", request.side.name)
                put("quantity", request.quantity.toString())
                request.price?.let { put("price", it.toString()) }
                put("traceId", traceId)
            })
            
            order = Order.create(
                userId = userId,
                symbol = request.getNormalizedSymbol(),
                orderType = request.orderType,
                side = request.side,
                quantity = request.quantity,
                price = request.price,
                traceId = traceId,
                uuidGenerator = uuidGenerator
            )
            
            orderValidator.validateOrThrow(order)
            
            val savedOrder = orderRepository.save(order)
            
            val duration = System.currentTimeMillis() - startTime
            orderMetrics.recordOrderCreation(duration)
            
            structuredLogger.info("Order created successfully",
                buildOrderContext(order = savedOrder, duration = duration)
            )
            
            createAndSaveOutboxEvent(savedOrder)
            return OrderResponse.from(savedOrder)
            
        } catch (ex: OrderValidationException) {
            orderMetrics.incrementValidationFailures()
            throw ex.withServiceContext(userId, request.symbol)
        } catch (ex: DataIntegrityViolationException) {
            handlePersistenceException(ex, order, userId, request.symbol, startTime)
        } catch (ex: Exception) {
            handleUnexpectedException(ex, order, userId, request.symbol, startTime, "order creation")
        }
    }
    
    fun cancelOrder(orderId: String, userId: String, reason: String = "User cancelled"): OrderResponse {
        val startTime = System.currentTimeMillis()
        var order: Order? = null
        
        return try {
            order = orderRepository.findByIdAndUserId(orderId, userId)
                ?: throw OrderNotFoundException("Order not found: $orderId")
                    .withContext("orderId", orderId)
                    .withContext("userId", userId)
            
            structuredLogger.info("Order cancellation started",
                buildMap {
                    put("orderId", orderId)
                    put("userId", userId)
                    put("currentStatus", order.status.name)
                    put("reason", reason)
                }
            )
            val cancelledOrder = order.cancel(reason)
            val savedOrder = orderRepository.save(cancelledOrder)
            
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Order cancelled successfully",
                buildOrderContext(
                    orderId = orderId,
                    userId = userId,
                    duration = duration,
                    additionalFields = mapOf("reason" to reason)
                )
            )
            
            createAndSaveCancelledOutboxEvent(savedOrder)
            OrderResponse.from(savedOrder)
            
        } catch(ex: OrderNotFoundException) {
            throw ex
        } catch (ex: DataIntegrityViolationException) {
            handlePersistenceException(ex, order, userId, order?.symbol ?: "", startTime)
                
        } catch (ex: Exception) {
            handleUnexpectedException(ex, order, userId, order?.symbol ?: "", startTime, "order cancellation")
        }
    }
    
    private fun createAndSaveOutboxEvent(order: Order): OrderOutboxEvent {
        val event = OrderCreatedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = order.id,
            occurredAt = Instant.now(),
            traceId = order.traceId,
            order = order.toDTO()
        )
        
        val outboxEvent = OrderOutboxEvent(
            eventId = event.eventId,
            aggregateId = order.id,
            eventType = "OrderCreated",
            payload = objectMapper.writeValueAsString(event),
            orderId = order.id,
            userId = order.userId
        )
        
        val savedOutboxEvent = outboxRepository.save(outboxEvent)
        
        structuredLogger.info("Outbox event created for OrderCreated",
            buildMap {
                put("eventId", event.eventId)
                put("orderId", order.id)
                put("userId", order.userId)
                put("traceId", order.traceId)
            }
        )
        
        return savedOutboxEvent
    }
    
    private fun createAndSaveCancelledOutboxEvent(order: Order): OrderOutboxEvent {
        val event = OrderCancelledEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = order.id,
            occurredAt = Instant.now(),
            traceId = order.traceId,
            orderId = order.id,
            userId = order.userId,
            reason = order.cancellationReason ?: "Unknown reason"
        )
        
        val outboxEvent = OrderOutboxEvent(
            eventId = event.eventId,
            aggregateId = order.id,
            eventType = "OrderCancelled",
            payload = objectMapper.writeValueAsString(event),
            orderId = order.id,
            userId = order.userId
        )
        
        val savedOutboxEvent = outboxRepository.save(outboxEvent)
        
        structuredLogger.info("Outbox event created for OrderCancelled",
            buildMap {
                put("eventId", event.eventId)
                put("orderId", order.id)
                put("userId", order.userId)
                put("traceId", order.traceId)
            }
        )
        
        return savedOutboxEvent
    }

    private fun buildOrderContext(
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

interface OrderMetrics {
    fun recordOrderCreation(durationMs: Long)
    fun incrementValidationFailures()
    fun incrementDatabaseErrors()
    fun incrementUnexpectedErrors()
    fun incrementEventPublications()
    fun incrementEventPublicationFailures()
}

fun OrderValidationException.withServiceContext(userId: String, symbol: String): OrderValidationException {
    this.withContext("serviceLayer", "OrderService")
    this.withContext("userId", userId)
    this.withContext("symbol", symbol)
    this.withContext("timestamp", System.currentTimeMillis().toString())
    return this
}