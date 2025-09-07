package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.cdc.config.CdcProperties
import com.trading.common.domain.saga.SagaStatus
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.connect.data.Struct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@Component
class OrderSagaConnector(
    private val cdcProperties: CdcProperties,
    private val objectMapper: ObjectMapper,
    private val healthIndicator: com.trading.cdc.health.CdcHealthIndicator
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var kafkaProducer: KafkaProducer<String, String>
    
    @PostConstruct
    fun init() {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cdcProperties.kafka.bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
            put(ProducerConfig.LINGER_MS_CONFIG, 10)
            put(ProducerConfig.BATCH_SIZE_CONFIG, 16384)
        }
        
        kafkaProducer = KafkaProducer(props)
        logger.info("OrderSagaConnector initialized with Kafka broker: ${cdcProperties.kafka.bootstrapServers}")
    }
    
    fun processSagaStateChange(sagaRecord: Struct) {
        try {
            val sagaId = sagaRecord.getString("saga_id")
            val tradeId = sagaRecord.getString("trade_id")
            val orderId = sagaRecord.getString("order_id")
            val userId = sagaRecord.getString("user_id")
            val symbol = sagaRecord.getString("symbol")
            val state = sagaRecord.getString("state")
            val eventType = sagaRecord.getString("event_type")
            val eventPayload = sagaRecord.getString("event_payload")
            val topic = sagaRecord.getString("topic")
            
            if (!shouldProcessSagaState(state)) {
                logger.debug("Skipping saga state: $sagaId with state: $state")
                return
            }
            
            logger.info("Processing saga state change: sagaId=$sagaId, orderId=$orderId, eventType=$eventType, state=$state, symbol=$symbol")
            
            val enrichedPayload = try {
                val payloadNode = objectMapper.readTree(eventPayload) as ObjectNode
                payloadNode.put("sagaId", sagaId)
                payloadNode.put("tradeId", tradeId)
                payloadNode.put("sagaState", state)
                payloadNode.put("eventType", eventType)
                objectMapper.writeValueAsString(payloadNode)
            } catch (e: Exception) {
                logger.warn("Failed to enrich payload, sending original: ${e.message}")
                eventPayload
            }
            
            val record = ProducerRecord(
                topic,
                symbol, // Use symbol as partition key for ordering
                enrichedPayload
            )
            
            kafkaProducer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error("Failed to send saga event $sagaId to Kafka: ${exception.message}", exception)
                } else {
                    logger.info("Successfully sent saga event $sagaId to Kafka topic ${metadata.topic()} partition ${metadata.partition()} offset ${metadata.offset()}")
                    healthIndicator.incrementEventsProcessed()
                    
                    // Log structured event for monitoring
                    logger.info("Saga event published", 
                        mapOf(
                            "sagaId" to sagaId,
                            "orderId" to orderId,
                            "userId" to userId,
                            "eventType" to eventType,
                            "state" to state,
                            "topic" to metadata.topic(),
                            "partition" to metadata.partition().toString(),
                            "offset" to metadata.offset().toString()
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing saga state change: ${e.message}", e)
        }
    }
    
    private fun shouldProcessSagaState(state: String): Boolean {
        return when (SagaStatus.valueOf(state)) {
            SagaStatus.STARTED,
            SagaStatus.IN_PROGRESS,
            SagaStatus.COMPLETED,
            SagaStatus.COMPENSATING,
            SagaStatus.COMPENSATED,
            SagaStatus.FAILED,
            SagaStatus.TIMEOUT -> true
        }
    }
    
    @PreDestroy
    fun shutdown() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            logger.info("OrderSagaConnector shutdown completed")
        } catch (e: Exception) {
            logger.error("Error during shutdown: ${e.message}", e)
        }
    }
}