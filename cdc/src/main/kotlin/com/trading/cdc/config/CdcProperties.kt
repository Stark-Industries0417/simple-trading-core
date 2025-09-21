package com.trading.cdc.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "cdc")
data class CdcProperties(
    val database: DatabaseProperties = DatabaseProperties(),
    val kafka: KafkaProperties = KafkaProperties(),
    val debezium: DebeziumProperties = DebeziumProperties()
)

data class DatabaseProperties(
    val hostname: String = "localhost",
    val port: Int = 3306,
    val name: String = "trading_db",
    val username: String = "root",
    val password: String = ""
)

data class KafkaProperties(
    val bootstrapServers: String = "localhost:9092",
    val orderEventsTopic: String = "order.events",
    val schemaHistoryTopic: String = "dbhistory.order"
)

data class DebeziumProperties(
    val serverId: String = "184054",
    val serverName: String = "order-service",
    val offsetStorageFileName: String = "/tmp/offsets.dat",
    val offsetFlushIntervalMs: Long = 1000,
    val pollIntervalMs: Long = 100,
    val maxBatchSize: Int = 1024,
    val snapshotMode: String = "schema_only"
)