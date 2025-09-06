package com.trading.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.order.OrderStatus
import com.trading.common.event.base.DomainEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.OrderValidator
import com.trading.order.domain.OrderCancellationValidator
import com.trading.order.domain.toDTO
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
    private val orderCancellationValidator: OrderCancellationValidator,
    private val orderServiceHelper: OrderServiceHelper
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
            orderServiceHelper.handlePersistenceException(ex, order, userId, request.symbol, startTime)
        } catch (ex: Exception) {
            orderServiceHelper.handleUnexpectedException(ex, order, userId, request.symbol, startTime, "order creation")
        }
    }

    fun cancelOrderWithSaga(orderId: String, userId: String, reason: String, traceId: String): OrderResponse {
        val order = orderCancellationValidator.validateAndRetrieveOrderForCancellation(orderId, userId)
        
        order.cancel(reason)
        val savedOrder = orderRepository.save(order)

        val cancelEvent = OrderCancelledEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = savedOrder.id,
            occurredAt = Instant.now(),
            traceId = traceId,
            orderId = savedOrder.id,
            userId = savedOrder.userId,
            reason = reason
        )

        val compensationSagaId = uuidGenerator.generateEventId()
        publishEventToOutbox(
            event = cancelEvent,
            orderId = savedOrder.id,
            userId = savedOrder.userId,
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
        
        return OrderResponse.from(savedOrder)
    }
    

    fun publishEventToOutbox(
        event: DomainEvent,
        orderId: String,
        userId: String,
        sagaId: String,
        tradeId: String? = null
    ) {
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