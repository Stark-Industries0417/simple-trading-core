package com.trading.order.application

import com.trading.common.dto.order.OrderStatus
import com.trading.common.event.saga.AccountUpdatedEvent
import com.trading.common.event.saga.AccountUpdateFailedEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.order.domain.OrderRepository
import com.trading.order.infrastructure.outbox.OrderOutboxEvent
import com.trading.order.infrastructure.outbox.OrderOutboxRepository
import com.trading.common.outbox.OutboxStatus
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
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    private val orderSagaService: OrderSagaService,
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
        
        orderSagaService.publishEventToOutbox(
            event = timeoutEvent,
            orderId = outboxEvent.orderId,
            userId = order?.userId ?: "",
            sagaId = outboxEvent.sagaId ?: uuidGenerator.generateEventId()
        )
    }
}