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
import java.math.BigDecimal

@Component
class OrderEventConsumer(
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(OrderEventConsumer::class.java)
    }
    
    @KafkaListener(
        topics = ["#{kafkaProperties.topics.orderEvents}"],
        groupId = "#{kafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleOrderCreatedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val outboxEvent = objectMapper.readTree(message)
            
            val eventType = outboxEvent.get("eventType")?.asText()
            if (eventType != "OrderCreated") {
                acknowledgment.acknowledge()
                return
            }
            
            val payloadJson = outboxEvent.get("payload")?.asText()
            if (payloadJson == null) {
                logger.error("Missing payload in OrderCreatedEvent - offset: {}", offset)
                acknowledgment.acknowledge()
                return
            }
            
            val orderEvent = try {
                objectMapper.readValue(payloadJson, OrderCreatedEvent::class.java)
            } catch (e: Exception) {
                logger.error("Failed to parse OrderCreatedEvent payload - offset: {}", offset, e)
                acknowledgment.acknowledge()
                return
            }
            
            val order = orderEvent.order
            
            structuredLogger.info("Processing order created event from Kafka",
                mapOf(
                    "orderId" to order.orderId,
                    "userId" to order.userId,
                    "symbol" to order.symbol,
                    "side" to order.side.toString(),
                    "quantity" to order.quantity.toString(),
                    "price" to (order.price?.toString() ?: "MARKET"),
                    "topic" to topic,
                    "partition" to partition.toString(),
                    "offset" to offset.toString(),
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
                                false
                            }
                        }
                    } else {
                        logger.warn("Market buy orders not yet supported for fund reservation")
                        true
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
                            false
                        }
                    }
                }
            }
            
            acknowledgment.acknowledge()
            
            if (!reservationSuccess) {
                logger.info("Order {} reservation failed, message acknowledged", order.orderId)
            }
            
        } catch (e: Exception) {
            logger.error(
                "Error processing order created event - topic: {}, partition: {}, offset: {}",
                topic, partition, offset, e
            )
            
            acknowledgment.acknowledge()
        }
    }
    
    @KafkaListener(
        topics = ["#{kafkaProperties.topics.orderEvents}"],
        groupId = "#{kafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleOrderCancelledEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val outboxEvent = objectMapper.readTree(message)
            
            val eventType = outboxEvent.get("eventType")?.asText()
            if (eventType != "OrderCancelled") {
                acknowledgment.acknowledge()
                return
            }
            
            val payloadJson = outboxEvent.get("payload")?.asText()
            if (payloadJson == null) {
                logger.error("Missing payload in OrderCancelledEvent - offset: {}", offset)
                acknowledgment.acknowledge()
                return
            }
            
            val cancelEvent = try {
                objectMapper.readValue(payloadJson, OrderCancelledEvent::class.java)
            } catch (e: Exception) {
                logger.error("Failed to parse OrderCancelledEvent payload - offset: {}", offset, e)
                acknowledgment.acknowledge()
                return
            }
            
            structuredLogger.info("Processing order cancelled event from Kafka",
                mapOf(
                    "orderId" to cancelEvent.orderId,
                    "userId" to cancelEvent.userId,
                    "reason" to cancelEvent.reason,
                    "topic" to topic,
                    "partition" to partition.toString(),
                    "offset" to offset.toString(),
                    "traceId" to cancelEvent.traceId
                )
            )
            
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
            
            acknowledgment.acknowledge()
            
        } catch (e: Exception) {
            logger.error(
                "Error processing order cancelled event - topic: {}, partition: {}, offset: {}",
                topic, partition, offset, e
            )
            
            acknowledgment.acknowledge()
        }
    }
}