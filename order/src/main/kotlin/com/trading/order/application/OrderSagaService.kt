package com.trading.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.order.OrderStatus
import com.trading.common.event.base.DomainEvent
import com.trading.common.outbox.OutboxStatus
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.exception.order.OrderPersistenceException
import com.trading.common.exception.order.OrderProcessingException
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.OrderValidator
import com.trading.order.domain.toDTO
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
@Transactional
class OrderSagaService(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OrderOutboxRepository,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    private val orderMetrics: OrderMetrics,
    private val orderValidator: OrderValidator,
    @Value("\${saga.timeouts.order:30}") private val orderTimeoutSeconds: Long = 30
) {
    
    fun createOrderWithSaga(request: CreateOrderRequest, userId: String, traceId: String): OrderResponse {
        val startTime = System.currentTimeMillis()

        structuredLogger.info("Order creation started", buildMap {
            put("userId", userId)
            put("symbol", request.symbol)
            put("orderType", request.orderType.name)
            put("side", request.side.name)
            put("quantity", request.quantity.toString())
            request.price?.let { put("price", it.toString()) }
            put("traceId", traceId)
        })
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
            order.status = OrderStatus.CREATED

            orderValidator.validateOrThrow(order)
            val savedOrder = orderRepository.save(order)

            val sagaId = uuidGenerator.generateEventId()
            val tradeId = uuidGenerator.generateEventId()

            val event = OrderCreatedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = savedOrder.id,
                occurredAt = Instant.now(),
                traceId = traceId,
                order = savedOrder.toDTO()
            )

            publishEventToOutbox(
                event = event,
                orderId = savedOrder.id,
                userId = savedOrder.userId,
                sagaId = sagaId,
                tradeId = tradeId
            )

            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Order created with saga",
                buildMap {
                    put("sagaId", sagaId)
                    put("orderId", savedOrder.id)
                    put("userId", userId)
                    put("symbol", savedOrder.symbol)
                    put("traceId", traceId)
                    put("duration", duration.toString())
                }
            )
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

    fun cancelOrderWithSaga(orderId: String, userId: String, reason: String, traceId: String) {
        val order = orderRepository.findById(orderId).orElseThrow {
            OrderProcessingException("Order not found: $orderId")
                .withContext("orderId", orderId)
                .withContext("userId", userId)
        }

        if (order.userId != userId) {
            throw OrderValidationException("Unauthorized to cancel this order")
                .withServiceContext(userId, order.symbol)
        }

        if (order.status !in listOf(OrderStatus.CREATED, OrderStatus.PARTIALLY_FILLED)) {
            throw OrderValidationException("Order cannot be cancelled in status: ${order.status}")
                .withServiceContext(userId, order.symbol)
        }

        order.cancel(reason)
        orderRepository.save(order)

        val cancelEvent = OrderCancelledEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = order.id,
            occurredAt = Instant.now(),
            traceId = traceId,
            orderId = order.id,
            userId = order.userId,
            reason = reason
        )

        val compensationSagaId = uuidGenerator.generateEventId()
        publishEventToOutbox(
            event = cancelEvent,
            orderId = order.id,
            userId = order.userId,
            sagaId = compensationSagaId
        )

        structuredLogger.info("Order cancelled with saga compensation",
            mapOf(
                "sagaId" to compensationSagaId,
                "orderId" to orderId,
                "userId" to userId,
                "reason" to reason,
                "traceId" to traceId
            )
        )
    }
    
    @KafkaListener(topics = ["account.events"], groupId = "order-saga-group")
    fun handleAccountEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return
            
            when (eventType) {
                "AccountUpdated" -> {
                    val event = objectMapper.readValue(message, AccountUpdatedEvent::class.java)
                    completeOrder(event)
                }
                "AccountUpdateFailed" -> {
                    val event = objectMapper.readValue(message, AccountUpdateFailedEvent::class.java)
                    cancelOrder(event)
                }
            }
        } catch (e: Exception) {
            structuredLogger.error("Error handling account event",
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "message" to message
                )
            )
        }
    }
    
    private fun completeOrder(event: AccountUpdatedEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) {
            structuredLogger.warn("Order not found for AccountUpdated event",
                mapOf("sagaId" to event.sagaId, "orderId" to event.orderId)
            )
            return
        }

        order.status = OrderStatus.COMPLETED
        order.filledQuantity = event.quantity
        order.filledAt = Instant.now()
        orderRepository.save(order)

        outboxRepository.findBySagaId(event.sagaId)?.let { outboxEvent ->
            outboxRepository.updateStatus(
                eventId = outboxEvent.eventId,
                status = OutboxStatus.PROCESSED,
                processedAt = Instant.now()
            )
        }
        
        structuredLogger.info("Order completed through saga",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to order.id,
                "tradeId" to event.tradeId,
                "amount" to event.amount.toString()
            )
        )
    }
    
    private fun cancelOrder(event: AccountUpdateFailedEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) {
            structuredLogger.warn("Order not found for AccountUpdateFailed event",
                mapOf("sagaId" to event.sagaId, "orderId" to event.orderId)
            )
            return
        }

        order.cancel(event.reason)
        orderRepository.save(order)

        outboxRepository.findBySagaId(event.sagaId)?.let { outboxEvent ->
            outboxRepository.updateStatus(
                eventId = outboxEvent.eventId,
                status = OutboxStatus.FAILED,
                processedAt = Instant.now()
            )
        }
        
        structuredLogger.info("Order cancelled due to account failure",
            mapOf(
                "sagaId" to event.sagaId,
                "orderId" to order.id,
                "reason" to event.reason,
                "failureType" to event.failureType.name
            )
        )
    }
    
    @Scheduled(fixedDelay = 5000)
    fun checkTimeouts() {
        val timeoutTime = Instant.now().minusSeconds(orderTimeoutSeconds)
        val timedOutSagas = outboxRepository.findTimedOutSagas(timeoutTime)
        
        timedOutSagas.forEach { outboxEvent ->
            try {
                handleTimeout(outboxEvent)
            } catch (e: Exception) {
                structuredLogger.error("Error handling saga timeout",
                    mapOf(
                        "sagaId" to (outboxEvent.sagaId ?: ""),
                        "orderId" to outboxEvent.orderId,
                        "error" to (e.message ?: "Unknown error")
                    )
                )
            }
        }
    }
    
    private fun handleTimeout(outboxEvent: OrderOutboxEvent) {
        structuredLogger.warn("Order saga timeout detected",
            mapOf(
                "sagaId" to (outboxEvent.sagaId ?: ""),
                "orderId" to outboxEvent.orderId,
                "status" to outboxEvent.status.name
            )
        )
        
        val order = orderRepository.findById(outboxEvent.orderId).orElse(null)
        if (order != null && order.status == OrderStatus.CREATED) {
            
            order.status = OrderStatus.TIMEOUT
            order.cancellationReason = "Saga timeout after $orderTimeoutSeconds seconds"
            orderRepository.save(order)
        }
        
        outboxRepository.updateStatus(
            eventId = outboxEvent.eventId,
            status = OutboxStatus.FAILED,
            processedAt = Instant.now()
        )
        
        val timeoutEvent = SagaTimeoutEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = outboxEvent.orderId,
            occurredAt = Instant.now(),
            traceId = order?.traceId ?: "",
            sagaId = outboxEvent.sagaId ?: uuidGenerator.generateEventId(),
            orderId = outboxEvent.orderId,
            tradeId = outboxEvent.tradeId,
            failedAt = "Order",
            timeoutDuration = orderTimeoutSeconds,
            metadata = mapOf("reason" to "Order processing timeout")
        )
        
        publishEventToOutbox(
            event = timeoutEvent,
            orderId = outboxEvent.orderId,
            userId = order?.userId ?: "",
            sagaId = outboxEvent.sagaId ?: uuidGenerator.generateEventId()
        )
    }

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

    private fun publishEventToOutbox(
        event: DomainEvent,
        orderId: String,
        userId: String,
        sagaId: String,
        tradeId: String? = null
    ) {
        // 이벤트 타입별 메타데이터 추출
        val (eventId, aggregateId, tradeId) = extractEventMetadata(event)
        
        val outboxEvent = OrderOutboxEvent(
            eventId = eventId,
            aggregateId = aggregateId,
            eventType = event.javaClass.simpleName,
            payload = objectMapper.writeValueAsString(event),
            orderId = orderId,
            userId = userId,
            sagaId = sagaId,
            tradeId = tradeId
        )
        
        outboxRepository.save(outboxEvent)
        
        structuredLogger.info("Outbox event published",
            buildMap {
                put("eventId", eventId)
                put("eventType", event.javaClass.simpleName)
                put("sagaId", sagaId)
                put("orderId", aggregateId)
                tradeId?.let { put("tradeId", it) }
                put("topic", "order.events")
            }
        )
    }
    
    /**
     * 이벤트 객체에서 메타데이터 추출
     */
    private fun extractEventMetadata(event: Any): Triple<String, String, String?> {
        return when (event) {
            is OrderCreatedEvent -> Triple(
                event.eventId,
                event.aggregateId,
                null
            )
            is SagaTimeoutEvent -> Triple(
                event.eventId,
                event.aggregateId,
                event.tradeId
            )
            is OrderCancelledEvent -> Triple(
                event.eventId,
                event.aggregateId,
                null
            )
            else -> Triple(
                uuidGenerator.generateEventId(),
                "", // fallback
                null
            )
        }
    }
}