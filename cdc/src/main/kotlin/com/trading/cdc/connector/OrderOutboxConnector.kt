package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.trading.cdc.config.CdcProperties
import com.trading.cdc.health.CdcHealthIndicator
import com.trading.common.outbox.EventTypes
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.connect.data.Struct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

data class OrderOutboxEventDto(
    val eventId: String,
    val sagaId: String,
    val eventType: String,
    val tradeId: String?,
    val orderId: String,
    val userId: String,
    val symbol: String,
    val orderType: String,
    val side: String,
    val quantity: String,
    val price: String?,
    val createdAt: String
)

@Component
class OrderOutboxConnector(
    private val cdcProperties: CdcProperties,
    private val objectMapper: ObjectMapper,
    private val healthIndicator: CdcHealthIndicator
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
            val outboxEvent = mapToOrderOutboxEvent(outboxRecord)

            val topic = determineTopicForEventType(outboxEvent.eventType)

            logger.info("Processing outbox event: eventId=${outboxEvent.eventId}, orderId=${outboxEvent.orderId}, " +
                    "eventType=${outboxEvent.eventType}, topic=$topic, sagaId=${outboxEvent.sagaId}, symbol=${outboxEvent.symbol}")

            val messageJson = objectMapper.writeValueAsString(outboxEvent)
            
            val record = ProducerRecord(
                topic,
                outboxEvent.symbol,
                messageJson
            )
            
            kafkaProducer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error("Failed to send event ${outboxEvent.eventId} to Kafka: ${exception.message}", exception)
                } else {
                    logger.info("Successfully sent event ${outboxEvent.eventId} to Kafka topic ${metadata.topic()} " +
                            "partition ${metadata.partition()} offset ${metadata.offset()}")
                    healthIndicator.incrementEventsProcessed()
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing outbox event: ${e.message}", e)
        }
    }
    
    private fun mapToOrderOutboxEvent(record: Struct): OrderOutboxEventDto {
        return OrderOutboxEventDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            tradeId = try { record.getString("trade_id") } catch (e: Exception) { null },
            orderId = record.getString("order_id"),
            userId = record.getString("user_id"),
            symbol = record.getString("symbol"),
            orderType = record.getString("order_type"),
            side = record.getString("side"),
            quantity = record.getString("quantity"),
            price = try { record.getString("price") } catch (e: Exception) { null },
            createdAt = record.getString("created_at")
        )
    }

    private fun determineTopicForEventType(eventType: String): String {

        return when (eventType) {
            EventTypes.Order.CREATED,
            EventTypes.Order.CANCELLED
                -> cdcProperties.kafka.orderEventsTopic
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