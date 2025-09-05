package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.domain.saga.SagaStatus
import com.trading.common.dto.order.OrderDTO
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderStatus
import com.trading.common.dto.order.OrderType
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.saga.TradeFailedEvent
import com.trading.common.event.saga.TradeRollbackEvent
import com.trading.common.event.saga.SagaTimeoutEvent
import com.trading.common.logging.StructuredLogger
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.saga.MatchingSagaRepository
import com.trading.matching.domain.saga.MatchingSagaState
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
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
        topics = ["order-service.orders"],
        groupId = "matching-saga-group"
    )
    fun handleOrderCDCEvent(record: ConsumerRecord<String, String>) {
        try {
            val cdcEvent = parseCDCEvent(record.value())
            when (cdcEvent.operation) {
                "c" -> {
                    if (cdcEvent.after?.status == OrderStatus.CREATED.name) {
                        processTrade(cdcEvent.after)
                    }
                }
                "u" -> {
                    when (cdcEvent.after?.status) {
                        OrderStatus.CANCELLED.name -> rollbackTrade(cdcEvent.after)
                        OrderStatus.TIMEOUT.name -> rollbackTrade(cdcEvent.after)
                    }
                }
            }
        } catch (e: Exception) {
            structuredLogger.error("Error processing CDC event",
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "offset" to record.offset().toString(),
                    "partition" to record.partition().toString()
                )
            )
        }
    }
    
    private fun processTrade(orderData: CDCOrderData) {
        val startTime = System.currentTimeMillis()
        var sagaId = orderData.sagaId
        if (sagaId == null) {
            sagaId = uuidGenerator.generateEventId()
        }
        val sagaState = MatchingSagaState(
            sagaId = sagaId,
            orderId = orderData.id,
            tradeId = uuidGenerator.generateEventId(),
            state = SagaStatus.IN_PROGRESS,
            timeoutAt = Instant.now().plusSeconds(matchingTimeoutSeconds),
            metadata = objectMapper.writeValueAsString(orderData)
        )
        val savedSaga = sagaRepository.save(sagaState)

        try {
            val orderDto = OrderDTO(
                orderId = orderData.id,
                userId = orderData.userId,
                symbol = orderData.symbol,
                orderType = OrderType.valueOf(orderData.orderType),
                side = OrderSide.valueOf(orderData.side),
                quantity = orderData.quantity.toBigDecimal(),
                price = orderData.price?.toBigDecimal(),
                traceId = orderData.traceId ?: "",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            val trades = matchingEngineManager.processOrderWithResult(orderDto, orderData.traceId ?: "")
            trades.forEach { trade ->
                val tradeEvent = TradeExecutedEvent(
                    eventId = uuidGenerator.generateEventId(),
                    aggregateId = trade.tradeId,
                    occurredAt = Instant.now(),
                    traceId = orderData.traceId ?: "",
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
                
                val eventNode = objectMapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(tradeEvent)
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
                        "orderId" to orderData.id,
                        "symbol" to trade.symbol,
                        "quantity" to trade.quantity.toString(),
                        "price" to trade.price.toString()
                    )
                )
            }
            savedSaga.markCompleted()
            sagaRepository.save(savedSaga)
            
            val duration = System.currentTimeMillis() - startTime
            structuredLogger.info("Matching completed successfully",
                mapOf(
                    "sagaId" to savedSaga.sagaId,
                    "orderId" to orderData.id,
                    "tradesCount" to trades.size.toString(),
                    "duration" to duration.toString()
                )
            )
            
        } catch (e: Exception) {
            savedSaga.markFailed(e.message)
            sagaRepository.save(savedSaga)

            val failedEvent = TradeFailedEvent(
                eventId = uuidGenerator.generateEventId(),
                aggregateId = orderData.id,
                occurredAt = Instant.now(),
                traceId = orderData.traceId ?: "",
                sagaId = savedSaga.sagaId,
                orderId = orderData.id,
                symbol = orderData.symbol,
                reason = e.message ?: "Unknown error",
                shouldRetry = false
            )
            kafkaTemplate.send(
                "trade.events",
                orderData.symbol,
                objectMapper.writeValueAsString(failedEvent)
            )
            structuredLogger.error("Matching failed",
                mapOf(
                    "sagaId" to savedSaga.sagaId,
                    "orderId" to orderData.id,
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }
    
    private fun rollbackTrade(orderData: CDCOrderData) {
        val saga = sagaRepository.findByOrderId(orderData.id)
        if (saga == null) {
            structuredLogger.warn("No saga found for order rollback",
                mapOf("orderId" to orderData.id)
            )
            return
        }
        if (saga.state == SagaStatus.COMPLETED) {
            val metadata = objectMapper.readValue(saga.metadata ?: "{}", Map::class.java)
            val tradeId = saga.tradeId
            try {
                val rollbackEvent = TradeRollbackEvent(
                    eventId = uuidGenerator.generateEventId(),
                    aggregateId = tradeId,
                    occurredAt = Instant.now(),
                    traceId = orderData.traceId ?: "",
                    sagaId = saga.sagaId,
                    tradeId = tradeId,
                    orderId = orderData.id,
                    buyOrderId = orderData.id,
                    sellOrderId = "",
                    symbol = orderData.symbol,
                    reason = "Order cancelled: ${orderData.cancellationReason}",
                    rollbackType = TradeRollbackEvent.RollbackType.FULL
                )
                kafkaTemplate.send(
                    "trade.events",
                    orderData.symbol,
                    objectMapper.writeValueAsString(rollbackEvent)
                )
                structuredLogger.info("Trade rollback event published",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "tradeId" to tradeId,
                        "orderId" to orderData.id,
                        "reason" to orderData.cancellationReason
                    )
                )
            } catch (e: Exception) {
                structuredLogger.error("Failed to rollback trade",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "orderId" to orderData.id,
                        "error" to (e.message ?: "Unknown error")
                    )
                )
            }
        }
        saga.markCompensated()
        sagaRepository.save(saga)
    }
    
    @Scheduled(fixedDelay = 3000)
    fun checkTimeouts() {
        val timedOutSagas = sagaRepository.findTimedOutSagas(
            listOf(SagaStatus.IN_PROGRESS),
            Instant.now()
        )
        timedOutSagas.forEach { saga ->
            try {
                handleTimeout(saga)
            } catch (e: Exception) {
                structuredLogger.error("Error handling saga timeout",
                    mapOf(
                        "sagaId" to saga.sagaId,
                        "orderId" to saga.orderId,
                        "error" to (e.message ?: "Unknown error")
                    )
                )
            }
        }
    }
    
    private fun handleTimeout(saga: MatchingSagaState) {
        structuredLogger.warn("Matching saga timeout detected",
            mapOf(
                "sagaId" to saga.sagaId,
                "orderId" to saga.orderId,
                "state" to saga.state.name
            )
        )
        saga.markTimeout()
        sagaRepository.save(saga)

        val timeoutEvent = SagaTimeoutEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = saga.orderId,
            occurredAt = Instant.now(),
            traceId = "",
            sagaId = saga.sagaId,
            orderId = saga.orderId,
            tradeId = saga.tradeId,
            failedAt = "Matching",
            timeoutDuration = matchingTimeoutSeconds,
            metadata = mapOf("reason" to "Matching timeout after ${matchingTimeoutSeconds} seconds")
        )
        
        kafkaTemplate.send(
            "saga.timeout.events",
            saga.orderId,
            objectMapper.writeValueAsString(timeoutEvent)
        )
    }
    
    private fun parseCDCEvent(json: String): CDCEvent {
        val node = objectMapper.readTree(json)
        return CDCEvent(
            operation = node.get("op")?.asText() ?: "",
            after = node.get("after")?.let { parseOrderData(it.toString()) }
        )
    }
    
    private fun parseOrderData(json: String): CDCOrderData {
        return objectMapper.readValue(json, CDCOrderData::class.java)
    }
    
    data class CDCEvent(
        val operation: String,
        val after: CDCOrderData?
    )
    
    data class CDCOrderData(
        val id: String,
        val userId: String,
        val symbol: String,
        val orderType: String,
        val side: String,
        val quantity: String,
        val price: String?,
        val status: String,
        val traceId: String?,
        val sagaId: String?,
        val cancellationReason: String = ""
    )
}