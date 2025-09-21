package com.trading.order.application

import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.cdc.account.AccountUpdatedDto
import com.trading.common.dto.cdc.account.AccountUpdateFailedDto
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class OrderConsumer(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OrderOutboxRepository,
    private val objectMapper: ObjectMapper
) {
    
    @KafkaListener(topics = ["account.events"], groupId = "order-saga-group")
    fun handleAccountEvent(message: String) {
        try {
            val jsonNode = objectMapper.readTree(message)
            val eventType = jsonNode.get("eventType")?.asText() ?: return
            
            when (eventType) {
                "AccountUpdated" -> {
                    val event = objectMapper.readValue(message, AccountUpdatedDto::class.java)
                    completeOrder(event)
                }
                "AccountUpdateFailed" -> {
                    val event = objectMapper.readValue(message, AccountUpdateFailedDto::class.java)
                    cancelOrder(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun completeOrder(event: AccountUpdatedDto) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.status = OrderStatus.COMPLETED
        order.filledQuantity = event.quantity
        order.filledAt = Instant.now()
        orderRepository.save(order)

        // Saga state and Outbox status updates are handled by CDC
        
    }
    
    private fun cancelOrder(event: AccountUpdateFailedDto) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.cancel(event.reason)
        orderRepository.save(order)

        // Saga state and Outbox status updates are handled by CDC
        
    }
    
    // Timeout handling moved to separate scheduler component
}