package com.trading.order.application

import com.trading.common.dto.order.OrderStatus
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.trading.common.outbox.OutboxStatus
import com.trading.common.domain.saga.SagaStatus
import com.trading.order.domain.saga.OrderSagaRepository
import com.trading.order.domain.saga.OrderSagaState
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class SagaEventListener(
    private val orderRepository: OrderRepository,
    private val outboxRepository: OrderOutboxRepository,
    private val sagaRepository: OrderSagaRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${saga.timeouts.order:30}") private val orderTimeoutSeconds: Long = 30
) {
    
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
            e.printStackTrace()
        }
    }
    
    private fun completeOrder(event: AccountUpdatedEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.status = OrderStatus.COMPLETED
        order.filledQuantity = event.quantity
        order.filledAt = Instant.now()
        orderRepository.save(order)

        sagaRepository.findBySagaId(event.sagaId)?.let { sagaState ->
            sagaState.markCompleted()
            sagaRepository.save(sagaState)
        }

        outboxRepository.findBySagaId(event.sagaId)?.let { outboxEvent ->
            outboxRepository.updateStatus(
                eventId = outboxEvent.eventId,
                status = OutboxStatus.PROCESSED,
                processedAt = Instant.now()
            )
        }
        
    }
    
    private fun cancelOrder(event: AccountUpdateFailedEvent) {
        val order = orderRepository.findById(event.orderId).orElse(null)
        if (order == null) return

        order.cancel(event.reason)
        orderRepository.save(order)

        sagaRepository.findBySagaId(event.sagaId)?.let { sagaState ->
            sagaState.markFailed(event.reason)
            sagaRepository.save(sagaState)
        }

        outboxRepository.findBySagaId(event.sagaId)?.let { outboxEvent ->
            outboxRepository.updateStatus(
                eventId = outboxEvent.eventId,
                status = OutboxStatus.FAILED,
                processedAt = Instant.now()
            )
        }
        
    }
    
    @Scheduled(fixedDelay = 5000)
    fun checkTimeouts() {
        val timedOutSagas = sagaRepository.findTimedOutSagas(
            states = listOf(SagaStatus.STARTED, SagaStatus.IN_PROGRESS),
            now = Instant.now()
        )

        timedOutSagas.forEach { saga ->
            try {
                handleSagaTimeout(saga)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun handleSagaTimeout(saga: OrderSagaState) {
        saga.markTimeout()
        sagaRepository.save(saga)
        
        val order = orderRepository.findById(saga.orderId).orElse(null)
        if (order != null && order.status == OrderStatus.CREATED) {
            order.status = OrderStatus.TIMEOUT
            order.cancellationReason = "Saga timeout after $orderTimeoutSeconds seconds"
            orderRepository.save(order)
        }
    }
}