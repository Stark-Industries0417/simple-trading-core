package com.trading.order.application

import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.exception.order.OrderNotFoundException
import com.trading.common.exception.order.OrderPersistenceException
import com.trading.common.exception.order.OrderProcessingException
import com.trading.common.exception.order.OrderValidationException
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
    ): Nothing {
        orderMetrics.incrementDatabaseErrors()

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
        operation: String
    ): Nothing {
        orderMetrics.incrementUnexpectedErrors()

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

            createAndSaveOutboxEvent(savedOrder)
            return OrderResponse.from(savedOrder)
            
        } catch (ex: OrderValidationException) {
            orderMetrics.incrementValidationFailures()
            throw ex.withServiceContext(userId, request.symbol)
        } catch (ex: DataIntegrityViolationException) {
            handlePersistenceException(ex, order, userId, request.symbol)
        } catch (ex: Exception) {
            handleUnexpectedException(ex, order, userId, request.symbol, "order creation")
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

            val cancelledOrder = order.cancel(reason)
            val savedOrder = orderRepository.save(cancelledOrder)

            createAndSaveCancelledOutboxEvent(savedOrder)
            OrderResponse.from(savedOrder)
            
        } catch(ex: OrderNotFoundException) {
            throw ex
        } catch (ex: DataIntegrityViolationException) {
            handlePersistenceException(ex, order, userId, order?.symbol ?: "")
                
        } catch (ex: Exception) {
            handleUnexpectedException(ex, order, userId, order?.symbol ?: "", "order cancellation")
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
        return outboxRepository.save(outboxEvent)
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
        return outboxRepository.save(outboxEvent)
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