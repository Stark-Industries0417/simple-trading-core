package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.saga.TradeFailedEvent
import com.trading.common.event.saga.TradeRollbackEvent
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.config.KafkaProperties
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
    private val uuidGenerator: UUIDv7Generator,
    private val kafkaProperties: KafkaProperties,
    @Value("\${saga.timeouts.matching:10}") private val matchingTimeoutSeconds: Long = 10
) {
    
    @KafkaListener(
        topics = ["#{@kafkaProperties.topics.orderEvents}"],
        groupId = "#{@kafkaProperties.consumer.groupId}"
    )
    fun handleOrderEvent(record: ConsumerRecord<String, String>) {
        try {
            val eventPayload = record.value()
            val jsonNode = objectMapper.readTree(eventPayload)
            
            val eventType = jsonNode.get("eventType")?.asText()
            if (eventType == null) return

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

                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun processOrderCreatedEvent(event: OrderCreatedEvent, sagaId: String, tradeId: String?) {
        val sagaState = MatchingSagaState(
            sagaId = sagaId,
            orderId = event.order.orderId,
            tradeId = tradeId ?: uuidGenerator.generateEventId(),
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = Instant.now().plusSeconds(matchingTimeoutSeconds),
            eventType = "TradeExecutedEvent",
            eventPayload = objectMapper.writeValueAsString(event)
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
                eventNode.put("eventType", "TradeExecutedEvent")
                
                kafkaTemplate.send(
                    kafkaProperties.topics.tradeEvents,
                    trade.symbol,
                    objectMapper.writeValueAsString(eventNode)
                )

            }
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
                kafkaProperties.topics.tradeEvents,
                event.order.symbol,
                objectMapper.writeValueAsString(failedEvent)
            )
        }
    }
    
    private fun processOrderCancelledEvent(event: OrderCancelledEvent, @Suppress("UNUSED_PARAMETER") sagaId: String?) {
        val saga = sagaRepository.findByOrderId(event.orderId)
        
        if (saga.state == SagaStatus.IN_PROGRESS) {
            try {
                val originalEvent = objectMapper.readValue(saga.eventPayload, OrderCreatedEvent::class.java)
                
                val cancelSuccess = matchingEngineManager.removeOrderFromBook(
                    orderId = event.orderId,
                    symbol = originalEvent.order.symbol,
                    traceId = event.traceId
                )
                if (cancelSuccess) {
                        
                } else {
                        
                }
                
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
                    kafkaProperties.topics.tradeEvents,
                    originalEvent.order.symbol,
                    objectMapper.writeValueAsString(rollbackEvent)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        saga.markCompensated()
        sagaRepository.save(saga)
    }
}