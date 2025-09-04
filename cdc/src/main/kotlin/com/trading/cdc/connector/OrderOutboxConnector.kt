package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.cdc.config.CdcProperties
import com.trading.common.outbox.OutboxStatus
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
class OrderOutboxConnector(
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
        logger.info("OrderOutboxConnector initialized with Kafka broker: ${cdcProperties.kafka.bootstrapServers}")
    }
    
    fun processOutboxEvent(outboxRecord: Struct) {
        try {
            val eventId = outboxRecord.getString("event_id")
            val aggregateId = outboxRecord.getString("aggregate_id")
            val aggregateType = outboxRecord.getString("aggregate_type")
            val eventType = outboxRecord.getString("event_type")
            val payload = outboxRecord.getString("payload")
            val status = outboxRecord.getString("status")
            
            if (status != OutboxStatus.PENDING.name) {
                logger.debug("Skipping non-pending event: $eventId with status: $status")
                return
            }
            
            val topic = determineTopicForEventType(eventType)
            
            logger.info("Processing outbox event: eventId=$eventId, aggregateId=$aggregateId, eventType=$eventType, topic=$topic")
            
            val record = ProducerRecord<String, String>(
                topic,
                aggregateId,
                payload
            )
            
            kafkaProducer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error("Failed to send event $eventId to Kafka: ${exception.message}", exception)
                } else {
                    logger.info("Successfully sent event $eventId to Kafka topic ${metadata.topic()} partition ${metadata.partition()} offset ${metadata.offset()}")
                    healthIndicator.incrementEventsProcessed()
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing outbox event: ${e.message}", e)
        }
    }
    
    private fun determineTopicForEventType(eventType: String): String {
        return when (eventType) {
            "OrderCreated" -> cdcProperties.kafka.orderEventsTopic
            "OrderCancelled" -> cdcProperties.kafka.orderEventsTopic
            else -> {
                logger.warn("Unknown event type: $eventType, using default topic")
                cdcProperties.kafka.orderEventsTopic
            }
        }
    }
    
    @PreDestroy
    fun shutdown() {
        try {
            kafkaProducer.flush()
            kafkaProducer.close()
            logger.info("OrderOutboxConnector shutdown completed")
        } catch (e: Exception) {
            logger.error("Error during shutdown: ${e.message}", e)
        }
    }
}