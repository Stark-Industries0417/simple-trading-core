package com.trading.order.application

import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.Order
import com.trading.order.domain.OrderRepository
import com.trading.order.domain.OrderValidator
import com.trading.order.domain.OrderCancellationValidator
import com.trading.order.infrastructure.web.dto.CreateOrderRequest
import com.trading.order.infrastructure.web.dto.OrderResponse
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
@Transactional
class OrderSagaService(
    private val orderRepository: OrderRepository,
    private val orderOutboxRepository: OrderOutboxRepository,
    private val uuidGenerator: UUIDv7Generator,
    private val orderValidator: OrderValidator,
    private val orderCancellationValidator: OrderCancellationValidator,
) {
    fun createOrderWithSaga(request: CreateOrderRequest, userId: String): OrderResponse {
        val order = Order.create(
            userId = userId,
            symbol = request.getNormalizedSymbol(),
            orderType = request.orderType,
            side = request.side,
            quantity = request.quantity,
            price = request.price,
            uuidGenerator = uuidGenerator
        )
        orderValidator.validateOrThrow(order)
        val savedOrder = orderRepository.save(order)

        val sagaId = uuidGenerator.generateEventId()
        order.toOutboxEvent(sagaId)
            .also { orderOutboxRepository.save(it) }

        return OrderResponse.from(savedOrder)
    }

    fun cancelOrderWithSaga(orderId: String, userId: String, reason: String): OrderResponse {
        val order = orderCancellationValidator.validateAndRetrieveOrderForCancellation(orderId, userId)

        order.cancel(reason)
        val savedOrder = orderRepository.save(order)
        val sagaId = uuidGenerator.generateEventId()
        order.toCancelledOutboxEvent(sagaId)
            .also { orderOutboxRepository.save(it) }

        return OrderResponse.from(savedOrder)
    }
}