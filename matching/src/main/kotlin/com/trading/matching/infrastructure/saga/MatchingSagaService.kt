package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.saga.TradeFailedEvent
import com.trading.common.event.saga.TradeRollbackEvent
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.saga.MatchingSagaRepository
import com.trading.matching.domain.saga.MatchingSagaState
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class MatchingSagaService(
    private val matchingEngineManager: MatchingEngineManager,
    private val sagaRepository: MatchingSagaRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger,
    private val uuidGenerator: UUIDv7Generator,
    @Value("\${saga.timeouts.matching:10}") private val matchingTimeoutSeconds: Long = 10
) {
    
    @KafkaListener(
        topics = ["order.events"],
        groupId = "matching-saga-group"
    )
    fun handleOrderEvent(record: ConsumerRecord<String, String>) {
        try {
            val eventPayload = record.value()
            val jsonNode = objectMapper.readTree(eventPayload)
            
            val eventType = jsonNode.get("eventType")?.asText()
            if (eventType == null) {
                structuredLogger.warn("Unknown event format, no eventType found",
                    mapOf(
                        "offset" to record.offset().toString(),
                        "partition" to record.partition().toString()
                    )
                )
                return
            }
            
            when (eventType) {
                "OrderCreatedEvent" -> {
                    val sagaId = jsonNode.get("sagaId").asText()
                    val tradeId = jsonNode.get("tradeId")?.asText()
                    
                    val event = objectMapper.readValue(eventPayload, OrderCreatedEvent::class.java)
                    processOrderCreatedEvent(event, sagaId, tradeId)
                }
                "OrderCancelledEvent" -> {
                    val sagaId = jsonNode.get("sagaId").asText()
                    val event = objectMapper.readValue(eventPayload, OrderCancelledEvent::class.java)
                    processOrderCancelledEvent(event, sagaId)
                }
                else -> {
                    structuredLogger.info("Ignoring event type: $eventType",
                        mapOf(
                            "eventType" to eventType,
                            "offset" to record.offset().toString()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            structuredLogger.error("Error processing order event",
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "offset" to record.offset().toString(),
                    "partition" to record.partition().toString(),
                    "topic" to record.topic()
                )
            )
        }
    }
    
    private fun processOrderCreatedEvent(event: OrderCreatedEvent, sagaId: String, tradeId: String?) {
        val startTime = System.currentTimeMillis()

        structuredLogger.info("Processing OrderCreatedEvent",
            mapOf(
                "eventId" to event.eventId,
                "sagaId" to sagaId,
                "orderId" to event.order.orderId,
                "symbol" to event.order.symbol,
                "traceId" to event.traceId
            )
        )
        
        val sagaState = MatchingSagaState(
            sagaId = sagaId,
            orderId = event.order.orderId,
            tradeId = tradeId ?: uuidGenerator.generateEventId(),
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = Instant.now().plusSeconds(matchingTimeoutSeconds),
            metadata = objectMapper.writeValueAsString(event)
        )
        val savedSaga = sagaRepository.save(sagaState)

        try {
            val orderDto = event.order
            
            val trades = matchingEngineManager.processOrderWithResult(orderDto, event.traceId)
            trades.forEach { trade ->
                val tradeEvent = TradeExecutedEvent(
                    eventId = uuidGenerator.generateEventId(),
                    aggregateId = trade.tradeId,
                    occurredAt = Instant.now(),
                    traceId = event.traceId,
                    tradeId = trade.tradeId,
                    symbol = trade.symbol,
                    buyOrderId = trade.buyOrderId,
                    sellOrderId = trade.sellOrderId,
                    buyUserId = trade.buyUserId,
                    sellUserId = trade.sellUserId,
                    price = trade.price,
                    quantity = trade.quantity,
                    timestamp = trade.timestamp
                )
                
                val eventNode = objectMapper.valueToTree<ObjectNode>(tradeEvent)
                eventNode.put("sagaId", savedSaga.sagaId)
                eventNode.put("eventType", "TradeExecuted")
                
                kafkaTemplate.send(
                    "trade.events",
                    trade.symbol,
                    objectMapper.writeValueAsString(eventNode)
                )
                
                structuredLogger.info("Trade executed and event published",
                    mapOf(
                        "sagaId" to savedSaga.sagaId,
                        "tradeId" to trade.tradeId,
                        "orderId" to event.order.orderId,
                        "symbol" to trade.symbol,
                        "quantity" to trade.quantity.toString(),
                        "price" to trade.price.toString()
                    )
                )
            }
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Matching completed successfully",
                mapOf(
                    "sagaId" to savedSaga.sagaId,
                    "orderId" to event.order.orderId,
                    "tradesCount" to trades.size.toString(),
                    "duration" to duration.toString()
                )
            )
            
        } catch (e: Exception) {
            savedSaga.markFailed(e.message)
            sagaRepository.save(savedSaga)

            val failedEvent = TradeFailedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = event.order.orderId,
                occurredAt = Instant.now(),
                traceId = event.traceId,
                sagaId = savedSaga.sagaId,
                orderId = event.order.orderId,
                symbol = event.order.symbol,
                reason = e.message ?: "Unknown error",
                shouldRetry = false
            )
            kafkaTemplate.send(
                "trade.events",
                event.order.symbol,
                objectMapper.writeValueAsString(failedEvent)
            )
            structuredLogger.error("Matching failed",
                mapOf(
                    "sagaId" to savedSaga.sagaId,
                    "orderId" to event.order.orderId,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
    
    private fun processOrderCancelledEvent(event: OrderCancelledEvent, sagaId: String?) {
        structuredLogger.info("Processing OrderCancelledEvent",
            mapOf(
                "eventId" to event.eventId,
                "orderId" to event.orderId,
                "reason" to event.reason
            )
        )
        
        val saga = sagaRepository.findByOrderId(event.orderId)
        
        if (saga.state == SagaStatus.IN_PROGRESS) {
            try {
                val originalEvent = objectMapper.readValue(saga.metadata ?: "{}", OrderCreatedEvent::class.java)
                
                val rollbackEvent = TradeRollbackEvent(
                    eventId = uuidGenerator.generateEventId(),
                    aggregateId = saga.tradeId,
                    occurredAt = Instant.now(),
                    traceId = event.traceId,
                    sagaId = saga.sagaId,
                    tradeId = saga.tradeId,
                    orderId = event.orderId,
                    buyOrderId = event.orderId,
                    sellOrderId = "",
                    symbol = originalEvent.order.symbol,
                    reason = "Order cancelled: ${event.reason}",
                    rollbackType = TradeRollbackEvent.RollbackType.FULL
                )
                kafkaTemplate.send(
                    "trade.events",
                    originalEvent.order.symbol,
                    objectMapper.writeValueAsString(rollbackEvent)
                )
                structuredLogger.info("Trade rollback event published",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "tradeId" to saga.tradeId,
                        "orderId" to event.orderId,
                        "reason" to event.reason
                    )
                )
            } catch (e: Exception) {
                structuredLogger.error("Failed to rollback trade",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "orderId" to event.orderId,
                        "error" to (e.message ?: "Unknown error")
                    )
                )
            }
        }
        saga.markCompensated()
        sagaRepository.save(saga)
    }
}