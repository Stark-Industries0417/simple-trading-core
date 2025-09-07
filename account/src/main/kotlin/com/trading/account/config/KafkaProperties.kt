package com.trading.account.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kafka")
data class KafkaProperties(
    var bootstrapServers: String = "localhost:9092",
    var schemaRegistryUrl: String? = null,
    
    var consumer: ConsumerProperties = ConsumerProperties(),
    var topics: TopicProperties = TopicProperties()
)

data class ConsumerProperties(
    var groupId: String = "account-service-group",
    var autoOffsetReset: String = "earliest",
    var enableAutoCommit: Boolean = false,
    var isolationLevel: String = "read_committed",
    var maxPollRecords: Int = 100,
    var sessionTimeoutMs: Int = 30000
)

data class TopicProperties(
    var orderEvents: String = "order.events",
    var tradeEvents: String = "trade.events"
)