package com.trading.matching.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kafka")
data class KafkaProperties(
    var bootstrapServers: String = "localhost:9092",
    var schemaRegistryUrl: String? = null,
    
    var producer: ProducerProperties = ProducerProperties(),
    var consumer: ConsumerProperties = ConsumerProperties(),
    var topics: TopicProperties = TopicProperties()
)

data class ProducerProperties(
    var acks: String = "all",
    var retries: Int = 3,
    var batchSize: Int = 16384,
    var lingerMs: Int = 10,
    var compressionType: String = "snappy",
    var idempotenceEnabled: Boolean = true,
    var transactionalId: String? = null,
    var maxInFlightRequestsPerConnection: Int = 1
)

data class ConsumerProperties(
    var groupId: String = "matching-engine-group",
    var autoOffsetReset: String = "earliest",
    var enableAutoCommit: Boolean = false,
    var isolationLevel: String = "read_committed",
    var maxPollRecords: Int = 100,
    var sessionTimeoutMs: Int = 30000
)

data class TopicProperties(
    var orderEvents: String = "order.events",
    var tradeEvents: String = "trade.events",
    var accountEvents: String = "account.events",
    var marketData: String = "market.data"
)