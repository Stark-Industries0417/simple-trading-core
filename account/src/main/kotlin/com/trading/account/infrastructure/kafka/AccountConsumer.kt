package com.trading.account.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.account.domain.ReservationResult
import com.trading.account.domain.StockReservationResult
import com.trading.common.dto.order.OrderSide
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.outbox.OutboxEventMessage
import com.trading.common.logging.StructuredLogger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

/**
 * 통합 이벤트 컨슈머
 * 
 * 단일 컨슈머에서 모든 이벤트 타입을 처리하여 순서 보장
 * - OrderCreatedEvent: 결제 예약
 * - TradeExecutedEvent: 체결 확정
 * - OrderCancelledEvent: 예약 취소
 */
@Component
class AccountConsumer(
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(AccountConsumer::class.java)
    }
    
    @KafkaListener(
        topics = ["#{kafkaProperties.topics.orderEvents}"],
        groupId = "#{kafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_KEY, required = false) key: String?,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            // OutboxEventMessage로 먼저 파싱
            val outboxMessage = objectMapper.readValue(message, OutboxEventMessage::class.java)
            
            structuredLogger.info("Processing event from Kafka",
                mapOf(
                    "eventType" to outboxMessage.eventType,
                    "aggregateId" to outboxMessage.aggregateId,
                    "symbol" to (key ?: "N/A"),
                    "topic" to topic,
                    "partition" to partition.toString(),
                    "offset" to offset.toString()
                )
            )
            
            // 이벤트 타입에 따른 분기 처리
            when (outboxMessage.eventType) {
                "OrderCreated" -> handleOrderCreated(outboxMessage, startTime)
                "TradeExecuted" -> handleTradeExecuted(outboxMessage, startTime)
                "OrderCancelled" -> handleOrderCancelled(outboxMessage, startTime)
                else -> {
                    logger.warn(
                        "Unknown event type: {} - offset: {}, partition: {}",
                        outboxMessage.eventType, offset, partition
                    )
                }
            }
            
            // 모든 처리가 성공하면 acknowledge
            acknowledgment.acknowledge()
            
        } catch (e: RetryableException) {
            // 재시도 가능한 예외는 다시 던져서 재처리
            logger.warn(
                "Retryable error processing event - offset: {}, partition: {}: {}",
                offset, partition, e.message
            )
            throw e
            
        } catch (e: Exception) {
            logger.error(
                "Error processing event - topic: {}, partition: {}, offset: {}",
                topic, partition, offset, e
            )
            // 복구 불가능한 에러는 acknowledge하여 진행
            acknowledgment.acknowledge()
        }
    }
    
    private fun handleOrderCreated(
        outboxMessage: OutboxEventMessage,
        startTime: Long
    ) {
        val orderEvent = objectMapper.readValue(
            outboxMessage.payload,
            OrderCreatedEvent::class.java
        )
        val order = orderEvent.order
        
        structuredLogger.info("Processing order created event",
            mapOf(
                "orderId" to order.orderId,
                "userId" to order.userId,
                "symbol" to order.symbol,
                "side" to order.side.toString(),
                "quantity" to order.quantity.toString(),
                "price" to (order.price?.toString() ?: "MARKET"),
                "traceId" to order.traceId
            )
        )
        
        val reservationSuccess = when (order.side) {
            OrderSide.BUY -> {
                val orderPrice = order.price
                if (orderPrice != null) {
                    val amount = orderPrice * order.quantity
                    val result = accountService.reserveFundsForOrder(
                        userId = order.userId,
                        amount = amount,
                        traceId = order.traceId
                    )
                    
                    when (result) {
                        is ReservationResult.Success -> {
                            structuredLogger.info("Funds reserved for buy order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "amount" to amount.toString(),
                                    "reservationId" to result.reservationId,
                                    "processingTimeMs" to (System.currentTimeMillis() - startTime).toString(),
                                    "traceId" to order.traceId
                                )
                            )
                            true
                        }
                        is ReservationResult.InsufficientFunds -> {
                            structuredLogger.warn("Insufficient balance for buy order",
                                mapOf(
                                    "orderId" to order.orderId,
                                    "userId" to order.userId,
                                    "required" to result.required.toString(),
                                    "available" to result.available.toString(),
                                    "traceId" to order.traceId
                                )
                            )
                            // TODO: 보상 이벤트 발행
                            false
                        }
                    }
                } else {
                    logger.warn("Market buy orders not yet supported for fund reservation")
                    true // 시장가 매수는 일단 통과
                }
            }
            
            OrderSide.SELL -> {
                val result = accountService.reserveStocksForOrder(
                    userId = order.userId,
                    symbol = order.symbol,
                    quantity = order.quantity,
                    traceId = order.traceId
                )
                
                when (result) {
                    is StockReservationResult.Success -> {
                        structuredLogger.info("Stocks reserved for sell order",
                            mapOf(
                                "orderId" to order.orderId,
                                "userId" to order.userId,
                                "symbol" to order.symbol,
                                "quantity" to order.quantity.toString(),
                                "reservationId" to result.reservationId,
                                "processingTimeMs" to (System.currentTimeMillis() - startTime).toString(),
                                "traceId" to order.traceId
                            )
                        )
                        true
                    }
                    is StockReservationResult.InsufficientShares -> {
                        structuredLogger.warn("Insufficient shares for sell order",
                            mapOf(
                                "orderId" to order.orderId,
                                "userId" to order.userId,
                                "symbol" to order.symbol,
                                "required" to result.required.toString(),
                                "available" to result.available.toString(),
                                "traceId" to order.traceId
                            )
                        )
                        // TODO: 보상 이벤트 발행
                        false
                    }
                }
            }
        }
        
        if (!reservationSuccess) {
            logger.info("Order {} reservation failed, continuing", order.orderId)
        }
    }
    
    private fun handleTradeExecuted(
        outboxMessage: OutboxEventMessage,
        startTime: Long
    ) {
        val tradeEvent = objectMapper.readValue(
            outboxMessage.payload,
            TradeExecutedEvent::class.java
        )
        
        structuredLogger.info("Processing trade executed event",
            mapOf(
                "tradeId" to tradeEvent.tradeId,
                "buyUserId" to tradeEvent.buyUserId,
                "sellUserId" to tradeEvent.sellUserId,
                "symbol" to tradeEvent.symbol,
                "quantity" to tradeEvent.quantity.toString(),
                "price" to tradeEvent.price.toString(),
                "traceId" to tradeEvent.traceId
            )
        )
        
        // 체결 처리 (예약된 결제 확정)
        val result = accountService.processTradeExecution(tradeEvent)
        
        when (result) {
            is AccountUpdateResult.Success -> {
                structuredLogger.info("Trade execution confirmed",
                    mapOf(
                        "tradeId" to tradeEvent.tradeId,
                        "buyerNewBalance" to result.buyerNewBalance.toString(),
                        "sellerNewBalance" to result.sellerNewBalance.toString(),
                        "processingTimeMs" to (System.currentTimeMillis() - startTime).toString(),
                        "traceId" to tradeEvent.traceId
                    )
                )
            }
            
            is AccountUpdateResult.Failure -> {
                structuredLogger.error("Trade execution failed",
                    mapOf(
                        "tradeId" to tradeEvent.tradeId,
                        "reason" to result.reason,
                        "shouldRetry" to result.shouldRetry.toString(),
                        "traceId" to tradeEvent.traceId
                    )
                )
                
                if (result.shouldRetry) {
                    throw RetryableException(
                        "Trade processing failed but is retryable: ${result.reason}"
                    )
                }
                // TODO: 보상 이벤트 발행 for non-retryable failures
            }
        }
    }
    
    private fun handleOrderCancelled(
        outboxMessage: OutboxEventMessage,
        startTime: Long
    ) {
        val cancelEvent = objectMapper.readValue(
            outboxMessage.payload,
            OrderCancelledEvent::class.java
        )
        
        structuredLogger.info("Processing order cancelled event",
            mapOf(
                "orderId" to cancelEvent.orderId,
                "userId" to cancelEvent.userId,
                "reason" to cancelEvent.reason,
                "traceId" to cancelEvent.traceId
            )
        )
        
        // 예약 해제
        val releaseSuccess = accountService.releaseReservation(
            userId = cancelEvent.userId,
            orderId = cancelEvent.orderId,
            traceId = cancelEvent.traceId
        )
        
        if (releaseSuccess) {
            structuredLogger.info("Reservation released for cancelled order",
                mapOf(
                    "orderId" to cancelEvent.orderId,
                    "userId" to cancelEvent.userId,
                    "processingTimeMs" to (System.currentTimeMillis() - startTime).toString(),
                    "traceId" to cancelEvent.traceId
                )
            )
        } else {
            structuredLogger.warn("No reservation found for cancelled order",
                mapOf(
                    "orderId" to cancelEvent.orderId,
                    "userId" to cancelEvent.userId,
                    "traceId" to cancelEvent.traceId
                )
            )
        }
    }
    
    /**
     * 재시도 가능한 예외
     * Kafka 리스너가 메시지를 다시 처리하도록 함
     */
    class RetryableException(message: String) : RuntimeException(message)
}