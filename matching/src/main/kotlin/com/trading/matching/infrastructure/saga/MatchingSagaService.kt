package com.trading.matching.infrastructure.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.order.OrderCreatedDto
import com.trading.common.dto.cdc.order.OrderCancelledDto
import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderType
import com.trading.common.outbox.EventTypes.Order.CANCELLED
import com.trading.common.outbox.EventTypes.Order.CREATED
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.domain.Matching
import com.trading.matching.domain.MatchingRepository
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import com.trading.matching.infrastructure.outbox.MatchingOutboxEvent
import com.trading.matching.infrastructure.outbox.MatchingOutboxRepository
import com.trading.matching.infrastructure.outbox.MatchingStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class MatchingSagaService(
    private val matchingEngineManager: MatchingEngineManager,
    private val matchingOutboxRepository: MatchingOutboxRepository,
    private val matchingRepository: MatchingRepository,
    private val uuidGenerator: UUIDv7Generator,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MatchingSagaService::class.java)

    @KafkaListener(
        topics = ["#{@matchingKafkaProperties.topics.orderEvents}"],
        groupId = "#{@matchingKafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleOrderEvent(
        record: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment
    ) {
        try {
            val eventPayload = record.value()
            val jsonNode = objectMapper.readTree(eventPayload)

            val eventType = jsonNode.get("eventType")?.asText()
            val sagaId = jsonNode.get("sagaId")?.asText()

            if (eventType == null || sagaId == null) {
                logger.warn("Invalid event - missing eventType or sagaId: {}", eventPayload)
                acknowledgment.acknowledge()
                return
            }

            if (isAlreadyProcessed(sagaId)) {
                logger.info("Event already processed for sagaId: {}", sagaId)
                acknowledgment.acknowledge()
                return
            }

            val success = when (eventType) {
                CREATED -> {
                    val event = objectMapper.readValue(eventPayload, OrderCreatedDto::class.java)
                    processOrderCreatedEvent(event)
                }
                CANCELLED -> {
                    val event = objectMapper.readValue(eventPayload, OrderCancelledDto::class.java)
                    processOrderCancelledEvent(event)
                }
                else -> {
                    logger.warn("Unknown event type: {}", eventType)
                    false
                }
            }

            if (success) {
                acknowledgment.acknowledge()
                logger.debug("Successfully processed and acknowledged event for sagaId: {}", sagaId)
            } else {
                logger.error("Failed to process event for sagaId: {}", sagaId)
                acknowledgment.acknowledge()
            }

        } catch (e: Exception) {
            logger.error("Error processing event: {}", e.message, e)
            acknowledgment.acknowledge()
        }
    }

    /**
     * sagaId로 이미 처리된 이벤트인지 확인 (멱등성 체크)
     */
    private fun isAlreadyProcessed(sagaId: String): Boolean {
        val existingEvents = matchingOutboxRepository.findBySagaId(sagaId)
        return existingEvents.isNotEmpty()
    }

    private fun processOrderCreatedEvent(event: OrderCreatedDto): Boolean {
        return try {
            val trades = matchingEngineManager.processOrderWithResult(event)

            if (trades.isEmpty()) {
                when (event.orderType) {
                    OrderType.MARKET -> {
                        logger.warn("Market order {} has no liquidity, rejecting order", event.orderId)

                        matchingEngineManager.removeOrderFromBook(event.orderId, event.symbol)

                        val failedMatching = createFailedMatching(event, "No liquidity for market order")
                        val savedFailedMatching = matchingRepository.save(failedMatching)

                        val failedEvent = savedFailedMatching.toFailedOutboxEvent(sagaId = event.sagaId)
                        matchingOutboxRepository.save(failedEvent)
                    }
                    OrderType.LIMIT -> {
                        logger.info("Limit order {} placed in order book, waiting for match", event.orderId)

                        val pendingMatching = Matching.createUnmatched(
                            orderId = event.orderId,
                            userId = event.userId,
                            symbol = event.symbol,
                            side = event.side,
                            quantity = event.quantity,
                            price = event.price ?: BigDecimal.ZERO,
                            uuidGenerator = uuidGenerator,
                            status = MatchingStatus.PENDING
                        )
                        matchingRepository.save(pendingMatching)
                        val noMatchEvent = MatchingOutboxEvent.createNoMatchEvent(
                            sagaId = event.sagaId,
                            orderId = event.orderId,
                            userId = event.userId,
                            symbol = event.symbol,
                            orderQuantity = event.quantity,
                            orderPrice = event.price ?: BigDecimal.ZERO,
                            tradeId = pendingMatching.tradeId,
                        )
                        matchingOutboxRepository.save(noMatchEvent)

                        logger.debug("Saved pending limit order matching and outbox event for orderId: {}", event.orderId)
                    }
                }
            } else {
                trades.forEach { trade ->
                    val matching = Matching.create(
                        buyOrderId = trade.buyOrderId,
                        sellOrderId = trade.sellOrderId,
                        buyUserId = trade.buyUserId,
                        sellUserId = trade.sellUserId,
                        symbol = trade.symbol,
                        quantity = trade.quantity,
                        price = trade.price,
                        uuidGenerator = uuidGenerator
                    )
                    val savedMatching = matchingRepository.save(matching)

                    val outboxEvent = savedMatching.toOutboxEvent(sagaId = event.sagaId)
                    matchingOutboxRepository.save(outboxEvent)

                    logger.debug("Saved matching entity and outbox event for tradeId: {}", savedMatching.tradeId)
                }
                logger.info("Processed order {} with {} matches", event.orderId, trades.size)
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to process OrderCreatedEvent for sagaId: {}", event.sagaId, e)

            try {
                val errorMatching = createFailedMatching(event, "Processing error: ${e.message}")
                val savedMatching = matchingRepository.save(errorMatching)

                val failedOutboxEvent = savedMatching.toFailedOutboxEvent(sagaId = event.sagaId)
                matchingOutboxRepository.save(failedOutboxEvent)
                true
            } catch (saveEx: Exception) {
                logger.error("Failed to save failure event for sagaId: {}", event.sagaId, saveEx)
                false
            }
        }
    }

    private fun processOrderCancelledEvent(event: OrderCancelledDto): Boolean {
        return try {
            matchingEngineManager.removeOrderFromBook(
                orderId = event.orderId,
                symbol = event.symbol,
            )

            val cancelledMatching = createFailedMatching(
                orderId = event.orderId,
                userId = event.userId,
                symbol = event.symbol,
                side = event.side,
                quantity = event.quantity,
                price = event.price,
                reason = "Order cancelled by user"
            )
            val savedMatching = matchingRepository.save(cancelledMatching)

            val cancelledOutboxEvent = savedMatching.toFailedOutboxEvent(sagaId = event.sagaId)
            matchingOutboxRepository.save(cancelledOutboxEvent)

            logger.debug("Successfully processed OrderCancelled event for sagaId: {}", event.sagaId)
            true
        } catch (e: Exception) {
            logger.error("Failed to process OrderCancelled event for sagaId: {}", event.sagaId, e)
            false
        }
    }


    private fun createFailedMatching(event: OrderCreatedDto, reason: String): Matching {
        return createFailedMatching(
            orderId = event.orderId,
            userId = event.userId,
            symbol = event.symbol,
            side = event.side,
            quantity = event.quantity,
            price = event.price,
            reason = reason
        )
    }

    private fun createFailedMatching(
        orderId: String,
        userId: String,
        symbol: String,
        side: OrderSide,
        quantity: BigDecimal,
        price: BigDecimal?,
        reason: String
    ): Matching {
        val matching = Matching.createUnmatched(
            orderId = orderId,
            userId = userId,
            symbol = symbol,
            side = side,
            quantity = quantity,
            price = price ?: BigDecimal.ZERO,
            uuidGenerator = uuidGenerator,
            status = MatchingStatus.PENDING
        )
        matching.markAsFailed(reason)
        return matching
    }
}