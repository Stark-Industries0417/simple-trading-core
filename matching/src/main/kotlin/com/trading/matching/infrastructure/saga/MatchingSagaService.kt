package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.order.OrderCreatedDto
import com.trading.common.dto.cdc.order.OrderCancelledDto
import com.trading.common.outbox.EventTypes.Order.CANCELLED
import com.trading.common.outbox.EventTypes.Order.CREATED
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import com.trading.matching.infrastructure.outbox.MatchingOutboxEvent
import com.trading.matching.infrastructure.outbox.MatchingOutboxRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class MatchingSagaService(
    private val matchingEngineManager: MatchingEngineManager,
    private val matchingOutboxRepository: MatchingOutboxRepository,
    private val objectMapper: ObjectMapper
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
                CREATED -> {
                    val event = objectMapper.readValue(eventPayload, OrderCreatedDto::class.java)
                    processOrderCreatedEvent(event)
                }
                CANCELLED -> {
                    val event = objectMapper.readValue(eventPayload, OrderCancelledDto::class.java)
                    processOrderCancelledEvent(event)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun processOrderCreatedEvent(event: OrderCreatedDto) {
        try {
            val trades = matchingEngineManager.processOrderWithResult(event)
            trades.forEach { trade ->
                val outboxEvent = MatchingOutboxEvent.createMatchedEvent(
                    sagaId = event.sagaId,
                    buyOrderId = trade.buyOrderId,
                    sellOrderId = trade.sellOrderId,
                    buyUserId = trade.buyUserId,
                    sellUserId = trade.sellUserId,
                    symbol = trade.symbol,
                    matchedQuantity = trade.quantity,
                    matchedPrice = trade.price
                )
                matchingOutboxRepository.save(outboxEvent)
            }
        } catch (e: Exception) {
            val failedOutboxEvent = MatchingOutboxEvent.createMatchingFailedEvent(
                sagaId = event.sagaId,
                buyOrderId = event.orderId,
                sellOrderId = "",
                buyUserId = event.userId,
                sellUserId = "",
                symbol = event.symbol,
                matchedQuantity = event.quantity,
                matchedPrice = event.price
            )
            matchingOutboxRepository.save(failedOutboxEvent)
        }
    }
    
    private fun processOrderCancelledEvent(event: OrderCancelledDto) {
        try {
            // 이미 처리된 매칭 이벤트가 있는지 확인 (중복 방지)
            matchingEngineManager.removeOrderFromBook(
                orderId = event.orderId,
                symbol = event.symbol,
            )

            val cancelledOutboxEvent = MatchingOutboxEvent.createMatchingFailedEvent(
                sagaId = event.sagaId,
                buyOrderId = event.orderId,
                sellOrderId = "",
                buyUserId = event.userId,
                sellUserId = "",
                symbol = event.symbol,
                matchedQuantity = BigDecimal(event.quantity),
                matchedPrice = event.price
            )
            matchingOutboxRepository.save(cancelledOutboxEvent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}