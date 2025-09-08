package com.trading.account.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountService
import com.trading.account.domain.ReservationResult
import com.trading.account.domain.StockReservationResult
import com.trading.common.dto.order.OrderSide
import com.trading.common.event.order.OrderCancelledEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.logging.StructuredLogger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component




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
        topics = ["#{@kafkaProperties.topics.orderEvents}"],
        groupId = "#{@kafkaProperties.consumer.groupId}",
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
            val jsonNode = objectMapper.readTree(message)
            
            if (!jsonNode.has("eventType")) {
                logger.warn(
                    "Message missing eventType field - offset: {}, partition: {}",
                    offset, partition
                )
                acknowledgment.acknowledge()
                return
            }
            
            val eventType = jsonNode.get("eventType").asText()
            
            structuredLogger.info("Processing event from Kafka",
                mapOf(
                    "eventType" to eventType,
                    "symbol" to (key ?: "N/A"),
                    "topic" to topic,
                    "partition" to partition.toString(),
                    "offset" to offset.toString()
                )
            )
            
            when (eventType) {
                "OrderCreatedEvent" -> {
                    val orderEvent = objectMapper.treeToValue(jsonNode, OrderCreatedEvent::class.java)
                    handleOrderCreated(orderEvent, startTime)
                }
                "OrderCancelledEvent" -> {
                    val cancelEvent = objectMapper.treeToValue(jsonNode, OrderCancelledEvent::class.java)
                    handleOrderCancelled(cancelEvent, startTime)
                }
                else -> {
                    logger.warn(
                        "Unknown event type: {} - offset: {}, partition: {}",
                        eventType, offset, partition
                    )
                }
            }
            acknowledgment.acknowledge()
            
        } catch (e: RetryableException) {
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
            acknowledgment.acknowledge()
        }
    }
    
    private fun handleOrderCreated(
        orderEvent: OrderCreatedEvent,
        startTime: Long
    ) {
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
                        orderId = order.orderId,
                        userId = order.userId,
                        symbol = order.symbol,
                        quantity = order.quantity,
                        price = orderPrice,
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
                            // 보상 이벤트는 Order 모듈에서 처리
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
                    orderId = order.orderId,
                    userId = order.userId,
                    symbol = order.symbol,
                    quantity = order.quantity,
                    price = order.price,
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
    private fun handleOrderCancelled(
        cancelEvent: OrderCancelledEvent,
        startTime: Long
    ) {
        
        structuredLogger.info("Processing order cancelled event",
            mapOf(
                "orderId" to cancelEvent.orderId,
                "userId" to cancelEvent.userId,
                "reason" to cancelEvent.reason,
                "traceId" to cancelEvent.traceId
            )
        )
        
        val releaseSuccess = accountService.releaseReservationByOrderId(
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