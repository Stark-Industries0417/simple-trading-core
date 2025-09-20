package com.trading.order.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.exception.order.OrderValidationException
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.OrderValidator
import com.trading.order.domain.OrderCancellationValidator
import com.trading.order.domain.toDTO
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import com.trading.order.domain.saga.OrderSagaRepository
import com.trading.order.domain.saga.OrderSagaState
import com.trading.common.domain.saga.SagaStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class OrderSagaService(
    private val orderRepository: OrderRepository,
    private val sagaRepository: OrderSagaRepository,
    private val objectMapper: ObjectMapper,
    private val uuidGenerator: UUIDv7Generator,
    private val orderMetrics: OrderMetrics,
    private val orderValidator: OrderValidator,
    private val orderCancellationValidator: OrderCancellationValidator,
    private val orderServiceHelper: OrderServiceHelper
) {
    fun createOrderWithSaga(request: CreateOrderRequest, userId: String, traceId: String): OrderResponse {
        val order = Order.create(
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

        val sagaId = uuidGenerator.generateEventId()
        val tradeId = uuidGenerator.generateEventId()

        val event = OrderCreatedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = savedOrder.id,
            occurredAt = Instant.now(),
            traceId = traceId,
            order = savedOrder.toDTO()
        )

        val sagaState = OrderSagaState(
            sagaId = sagaId,
            tradeId = tradeId,
            orderId = savedOrder.id,
            userId = userId,
            symbol = savedOrder.symbol,
            orderType = savedOrder.orderType.name,
            state = SagaStatus.STARTED,
            timeoutAt = Instant.now().plusSeconds(30),
            eventType = "OrderCreatedEvent",
            eventPayload = objectMapper.writeValueAsString(event)
        )
        sagaRepository.save(sagaState)

        return OrderResponse.from(savedOrder)
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

        val compensationSagaState = OrderSagaState(
            sagaId = compensationSagaId,
            tradeId = uuidGenerator.generateEventId(),
            orderId = savedOrder.id,
            userId = savedOrder.userId,
            symbol = savedOrder.symbol,
            orderType = savedOrder.orderType.name,
            state = SagaStatus.COMPENSATING,
            timeoutAt = Instant.now().plusSeconds(30),
            eventType = "OrderCancelledEvent",
            eventPayload = objectMapper.writeValueAsString(cancelEvent)
        )
        sagaRepository.save(compensationSagaState)
        return OrderResponse.from(savedOrder)
    }
}