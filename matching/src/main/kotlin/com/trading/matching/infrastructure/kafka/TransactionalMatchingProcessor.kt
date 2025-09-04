package com.trading.matching.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.event.matching.TradeExecutedEvent
import com.trading.common.event.order.OrderCreatedEvent
import com.trading.common.event.outbox.OutboxEventMessage
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.config.KafkaProperties
import com.trading.matching.domain.Trade
import com.trading.matching.infrastructure.engine.MatchingEngineManager
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Kafka Transactional Matching Processor
 * 
 * Native Kafka를 직접 사용하여 Exactly-Once 처리를 보장합니다.
 * Spring Kafka 대신 Native Kafka를 사용하는 이유:
 * - 트랜잭션 처리에 대한 세밀한 제어
 * - 오프셋 커밋 타이밍 정확한 제어
 * - 매칭 엔진의 Lock-free 특성과 일관성 유지
 */
@Component
class TransactionalMatchingProcessor(
    private val kafkaProperties: KafkaProperties,
    private val matchingEngineManager: MatchingEngineManager,
    private val objectMapper: ObjectMapper,
    private val uuidGenerator: UUIDv7Generator
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(TransactionalMatchingProcessor::class.java)
        private const val POLL_TIMEOUT_MS = 100L
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10L
    }
    
    private lateinit var consumer: KafkaConsumer<String, String>
    private lateinit var producer: KafkaProducer<String, String>
    private val transactionalId = "matching-engine-${UUID.randomUUID()}"
    
    @Volatile
    private var running = true
    
    @PostConstruct
    fun initialize() {
        logger.info("Initializing TransactionalMatchingProcessor with transactionalId: $transactionalId")
        
        initializeConsumer()
        initializeProducer()
        
        logger.info(
            "TransactionalMatchingProcessor initialized - transactionalId: {}, consumerGroup: {}, orderEvents: {}, tradeEvents: {}",
            transactionalId,
            kafkaProperties.consumer.groupId,
            kafkaProperties.topics.orderEvents,
            kafkaProperties.topics.tradeEvents
        )
    }
    
    private fun initializeConsumer() {
        consumer = KafkaConsumer(createConsumerProperties())
        consumer.subscribe(listOf(kafkaProperties.topics.orderEvents))
    }
    
    private fun initializeProducer() {
        producer = KafkaProducer(createProducerProperties())
        producer.initTransactions()
    }
    
    private fun createConsumerProperties() = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers)
        put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumer.groupId)

        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)

        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")

        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.consumer.autoOffsetReset)
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.consumer.maxPollRecords)
        put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaProperties.consumer.sessionTimeoutMs)
    }
    
    private fun createProducerProperties() = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.bootstrapServers)

        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)

        put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId)
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        put(ProducerConfig.ACKS_CONFIG, kafkaProperties.producer.acks)
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, kafkaProperties.producer.maxInFlightRequestsPerConnection)

        put(ProducerConfig.RETRIES_CONFIG, kafkaProperties.producer.retries)
        put(ProducerConfig.BATCH_SIZE_CONFIG, kafkaProperties.producer.batchSize)
        put(ProducerConfig.LINGER_MS_CONFIG, kafkaProperties.producer.lingerMs)
        put(ProducerConfig.COMPRESSION_TYPE_CONFIG, kafkaProperties.producer.compressionType)
    }
    
    @Scheduled(fixedDelay = 100)
    fun processOrders() {
        if (!running) return
        
        try {
            val records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS))
            if (records.isEmpty) return
            
            processRecordsBatch(records)
            
        } catch (e: Exception) {
            logger.error(
                "Error in main processing loop: ${e.message}",
                e
            )
        }
    }
    
    private fun processRecordsBatch(records: ConsumerRecords<String, String>) {
        val startTime = System.currentTimeMillis()

        producer.beginTransaction()
        
        try {
            val processedOffsets = mutableMapOf<TopicPartition, OffsetAndMetadata>()
            var totalTrades = 0
            
            records.forEach { record ->
                try {
                    val outboxMessage = objectMapper.readValue(record.value(), OutboxEventMessage::class.java)
                    val orderEvent = objectMapper.readValue(outboxMessage.payload, OrderCreatedEvent::class.java)
                    
                    logger.debug(
                        "Processing order from Kafka - eventType: {}, aggregateId: {}, eventId: {}, orderId: {}, symbol: {}, offset: {}, partition: {}",
                        outboxMessage.eventType,
                        outboxMessage.aggregateId,
                        orderEvent.eventId,
                        orderEvent.order.orderId,
                        orderEvent.order.symbol,
                        record.offset(),
                        record.partition()
                    )

                    val trades = processOrder(orderEvent)
                    trades.forEach { trade ->
                        val tradeEvent = createTradeEvent(trade, orderEvent.traceId)
                        
                        val tradeRecord = ProducerRecord(
                            kafkaProperties.topics.tradeEvents,
                            trade.symbol,
                            objectMapper.writeValueAsString(tradeEvent)
                        )
                        
                        producer.send(tradeRecord)
                        totalTrades++
                        
                        logger.info(
                            "Trade event sent to Kafka - tradeId: {}, symbol: {}, price: {}, quantity: {}, traceId: {}",
                            trade.tradeId,
                            trade.symbol,
                            trade.price,
                            trade.quantity,
                            orderEvent.traceId
                        )
                    }

                    val topicPartition = TopicPartition(record.topic(), record.partition())
                    processedOffsets[topicPartition] = OffsetAndMetadata(record.offset() + 1)
                    
                } catch (e: Exception) {
                    logger.error(
                        "Error processing individual record - offset: {}, partition: {}",
                        record.offset(),
                        record.partition(),
                        e
                    )
                    throw e
                }
            }

            producer.sendOffsetsToTransaction(processedOffsets, consumer.groupMetadata())
            producer.commitTransaction()
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info(
                "Transaction committed successfully - recordsProcessed: {}, tradesGenerated: {}, processingTimeMs: {}",
                records.count(),
                totalTrades,
                processingTime
            )
            
        } catch (e: Exception) {
            producer.abortTransaction()
            logger.error(
                "Transaction aborted due to error - recordCount: {}",
                records.count(),
                e
            )
        }
    }
    
    private fun processOrder(event: OrderCreatedEvent): List<Trade> {
        val trades = matchingEngineManager.processOrderWithResult(event.order, event.traceId)
        
        if (trades.isEmpty()) {
            logger.debug(
                "No trades generated for order - orderId: {}, symbol: {}, orderType: {}, traceId: {}",
                event.order.orderId,
                event.order.symbol,
                event.order.orderType,
                event.traceId
            )
        }
        
        return trades
    }
    
    private fun createTradeEvent(trade: Trade, traceId: String): TradeExecutedEvent {
        return TradeExecutedEvent(
            eventId = uuidGenerator.generateEventId(),
            aggregateId = trade.tradeId,
            occurredAt = Instant.now(),
            traceId = traceId,
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
    }
    
    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down TransactionalMatchingProcessor")
        running = false
        
        try {
            consumer.close(Duration.ofSeconds(SHUTDOWN_TIMEOUT_SECONDS))
            producer.close(Duration.ofSeconds(SHUTDOWN_TIMEOUT_SECONDS))
            logger.info("TransactionalMatchingProcessor shutdown complete")
        } catch (e: Exception) {
            logger.error(
                "Error during shutdown",
                e
            )
        }
    }
}