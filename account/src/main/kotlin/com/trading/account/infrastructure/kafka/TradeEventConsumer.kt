package com.trading.account.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.account.application.AccountService
import com.trading.account.application.AccountUpdateResult
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.logging.StructuredLogger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class TradeEventConsumer(
    private val accountService: AccountService,
    private val objectMapper: ObjectMapper,
    private val structuredLogger: StructuredLogger
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(TradeEventConsumer::class.java)
    }
    
    @KafkaListener(
        topics = ["#{kafkaProperties.topics.orderEvents}"],
        groupId = "#{kafkaProperties.consumer.groupId}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleTradeExecutedEvent(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val tradeEvent = objectMapper.readValue(message, TradeExecutedEvent::class.java)
            
            structuredLogger.info("Processing trade event from Kafka",
                mapOf(
                    "tradeId" to tradeEvent.tradeId,
                    "buyUserId" to tradeEvent.buyUserId,
                    "sellUserId" to tradeEvent.sellUserId,
                    "symbol" to tradeEvent.symbol,
                    "quantity" to tradeEvent.quantity.toString(),
                    "price" to tradeEvent.price.toString(),
                    "topic" to topic,
                    "partition" to partition.toString(),
                    "offset" to offset.toString(),
                    "traceId" to tradeEvent.traceId
                )
            )
            
            val result = accountService.processTradeExecution(tradeEvent)
            
            when (result) {
                is AccountUpdateResult.Success -> {
                    structuredLogger.info("Account update successful",
                        mapOf(
                            "tradeId" to tradeEvent.tradeId,
                            "buyerNewBalance" to result.buyerNewBalance.toString(),
                            "sellerNewBalance" to result.sellerNewBalance.toString(),
                            "processingTimeMs" to (System.currentTimeMillis() - startTime).toString(),
                            "traceId" to tradeEvent.traceId
                        )
                    )
                    
                    acknowledgment.acknowledge()
                }
                
                is AccountUpdateResult.Failure -> {
                    structuredLogger.error("Account update failed",
                        mapOf(
                            "tradeId" to tradeEvent.tradeId,
                            "reason" to result.reason,
                            "shouldRetry" to result.shouldRetry.toString(),
                            "traceId" to tradeEvent.traceId
                        )
                    )
                    
                    if (result.shouldRetry) {
                        logger.warn("Trade {} marked for retry, not acknowledging message", tradeEvent.tradeId)
                        throw RetryableException("Trade processing failed but is retryable: ${result.reason}")
                    } else {
                        logger.error("Trade {} failed with non-retryable error, acknowledging to proceed", tradeEvent.tradeId)
                        acknowledgment.acknowledge()
                    }
                }
            }
            
        } catch (e: RetryableException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Error processing trade event - topic: {}, partition: {}, offset: {}",
                topic, partition, offset, e
            )
            
            acknowledgment.acknowledge()
        }
    }
    
    class RetryableException(message: String) : RuntimeException(message)
}