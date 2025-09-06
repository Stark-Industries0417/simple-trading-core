package com.trading.order.domain

import com.trading.common.dto.order.OrderStatus
import com.trading.common.exception.order.OrderNotFoundException
import com.trading.common.exception.order.OrderValidationException
import com.trading.order.application.withServiceContext
import org.springframework.stereotype.Component

@Component
class OrderCancellationValidator(
    private val orderRepository: OrderRepository
) {
    
    fun validateAndRetrieveOrderForCancellation(orderId: String, userId: String): Order {
        val order = orderRepository.findById(orderId).orElseThrow {
            OrderNotFoundException("Order not found: $orderId")
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
        
        return order
    }
    
    fun validateAndRetrieveOrderForCancellationByUserId(orderId: String, userId: String): Order {
        val order = orderRepository.findByIdAndUserId(orderId, userId)
            ?: throw OrderNotFoundException("Order not found: $orderId")
                .withContext("orderId", orderId)
                .withContext("userId", userId)

        if (order.status !in listOf(OrderStatus.CREATED, OrderStatus.PARTIALLY_FILLED)) {
            throw OrderValidationException("Order cannot be cancelled in status: ${order.status}")
                .withServiceContext(userId, order.symbol)
        }
        
        return order
    }
}