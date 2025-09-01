package com.trading.order.application

import com.trading.common.event.base.EventPublisher
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.exception.*
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.*
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant




@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderValidator: OrderValidator,
    private val eventPublisher: EventPublisher,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    private val orderMetrics: OrderMetrics
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(OrderService::class.java)
    }
    
    fun createOrder(request: CreateOrderRequest, userId: String, traceId: String): OrderResponse {
        val startTime = System.currentTimeMillis()
        var order: Order? = null
        
        try {
            structuredLogger.info("Order creation started",
                buildMap {
                    put("userId", userId)
                    put("symbol", request.symbol)
                    put("orderType", request.orderType.name)
                    put("side", request.side.name)
                    put("quantity", request.quantity.toString())
                    request.price?.let { put("price", it.toString()) }
                    put("traceId", traceId)
                }
            )
            
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
                buildMap {
                    put("orderId", savedOrder.id)
                    put("userId", savedOrder.userId)
                    put("symbol", savedOrder.symbol)
                    put("side", savedOrder.side.name)
                    put("orderType", savedOrder.orderType.name)
                    put("quantity", savedOrder.quantity.toString())
                    savedOrder.price?.let { put("price", it.toString()) }
                    put("status", savedOrder.status.name)
                    put("duration", duration.toString())
                    put("version", savedOrder.version.toString())
                }
            )
            
            publishOrderCreatedEvent(savedOrder)
            
            return OrderResponse.from(savedOrder)
            
        } catch (ex: OrderValidationException) {
            orderMetrics.incrementValidationFailures()
            throw ex.withServiceContext(userId, request.symbol)
        } catch (ex: DataIntegrityViolationException) {
            val duration = System.currentTimeMillis() - startTime
            orderMetrics.incrementDatabaseErrors()
            
            structuredLogger.error("Order persistence failed: constraint violation",
                buildMap {
                    order?.id?.let { put("orderId", it) }
                    put("userId", userId)
                    put("symbol", request.symbol)
                    put("duration", duration.toString())
                    put("error", ex.message ?: "Unknown error")
                    put("constraintViolation", true)
                }
            )
            val exception = OrderPersistenceException("Failed to save order: constraint violation", ex)
                .withContext("userId", userId)
                .withContext("symbol", request.symbol)
            order?.id?.let { exception.withContext("orderId", it) }
            throw exception
        } catch (ex: Exception) {
            val duration = System.currentTimeMillis() - startTime
            orderMetrics.incrementUnexpectedErrors()

            structuredLogger.error("Unexpected error during order creation",
                buildMap {
                    order?.id?.let { put("orderId", it) }
                    put("userId", userId)
                    put("symbol", request.symbol)
                    put("duration", duration.toString())
                    put("error", ex.message ?: "Unknown error")
                    put("exceptionType", ex.javaClass.simpleName)
                }
            )

            val exception = OrderProcessingException("Order creation failed due to unexpected error", ex)
                .withContext("userId", userId)
                .withContext("symbol", request.symbol)
            order?.id?.let { exception.withContext("orderId", it) }
            throw exception
        }
    }
    
    fun cancelOrder(orderId: String, userId: String, reason: String = "User cancelled"): OrderResponse {
        val startTime = System.currentTimeMillis()
        
        return try {
            val order = orderRepository.findByIdAndUserId(orderId, userId)
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
                buildMap {
                    put("orderId", orderId)
                    put("userId", userId)
                    put("reason", reason)
                    put("duration", duration.toString())
                }
            )
            
            publishOrderCancelledEvent(savedOrder)
            
            OrderResponse.from(savedOrder)
            
        } catch(ex: OrderNotFoundException) {
          throw ex
        } catch (ex: DataIntegrityViolationException) {
            val duration = System.currentTimeMillis() - startTime
            orderMetrics.incrementDatabaseErrors()
            
            structuredLogger.error("Order cancellation failed: constraint violation",
                buildMap {
                    put("orderId", orderId)
                    put("userId", userId)
                    put("duration", duration.toString())
                    put("error", ex.message ?: "Unknown error")
                    put("constraintViolation", true)
                }
            )
            
            throw OrderPersistenceException("Failed to save cancelled order: constraint violation", ex)
                .withContext("orderId", orderId)
                .withContext("userId", userId)
                
        } catch (ex: Exception) {
            val duration = System.currentTimeMillis() - startTime
            orderMetrics.incrementUnexpectedErrors()

            structuredLogger.error("Unexpected error during order cancellation",
                buildMap {
                    put("orderId", orderId)
                    put("userId", userId)
                    put("duration", duration.toString())
                    put("error", ex.message ?: "Unknown error")
                    put("exceptionType", ex.javaClass.simpleName)
                }
            )

            throw OrderProcessingException("Order cancellation failed due to unexpected error", ex)
                .withContext("orderId", orderId)
                .withContext("userId", userId)
        }
    }
    
    private fun publishOrderCreatedEvent(order: Order) {
        try {
            val event = OrderCreatedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = order.id,
                occurredAt = Instant.now(),
                traceId = order.traceId,
                order = order.toDTO()
            )
            
            eventPublisher.publish(event)
            orderMetrics.incrementEventPublications()
            
            structuredLogger.info("OrderCreatedEvent published",
                mapOf(
                    "eventId" to event.eventId,
                    "orderId" to order.id,
                    "traceId" to order.traceId
                )
            )
            
        } catch (ex: Exception) {
            orderMetrics.incrementEventPublicationFailures()
            structuredLogger.warn("Failed to publish OrderCreatedEvent",
                buildMap {
                    put("orderId", order.id)
                    put("traceId", order.traceId)
                    put("errorType", ex.javaClass.simpleName)
                    ex.message?.let { put("error", it) }
                }
            )
        }
    }
    
    private fun publishOrderCancelledEvent(order: Order) {
        try {
            val event = OrderCancelledEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = order.id,
                occurredAt = Instant.now(),
                traceId = order.traceId,
                orderId = order.id,
                userId = order.userId,
                reason = order.cancellationReason ?: "Unknown reason"
            )
            
            eventPublisher.publish(event)
            
            structuredLogger.info("OrderCancelledEvent published",
                buildMap {
                    put("eventId", event.eventId)
                    put("orderId", order.id)
                    put("traceId", order.traceId)
                }
            )
            
        } catch (ex: Exception) {
            structuredLogger.warn("Failed to publish OrderCancelledEvent",
                buildMap {
                    put("orderId", order.id)
                    put("traceId", order.traceId)
                    put("errorType", ex.javaClass.simpleName)
                    put("error", ex.message ?: "Unknown error")
                }
            )
        }
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