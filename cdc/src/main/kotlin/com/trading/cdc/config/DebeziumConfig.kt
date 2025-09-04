package com.trading.cdc.config

import com.trading.cdc.connector.OrderOutboxConnector
import io.debezium.config.Configuration
import io.debezium.embedded.Connect
import io.debezium.engine.DebeziumEngine
import io.debezium.engine.RecordChangeEvent
import io.debezium.engine.format.ChangeEventFormat
import org.apache.kafka.connect.data.Struct
import org.apache.kafka.connect.source.SourceRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.*

@Component
class DebeziumConfig(
    private val cdcProperties: CdcProperties,
    private val orderOutboxConnector: OrderOutboxConnector
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun debeziumConfiguration(): Configuration {
        val props = Properties().apply {
            put("name", "order-outbox-connector")
            put("connector.class", "io.debezium.connector.mysql.MySqlConnector")
            put("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
            put("offset.storage.file.filename", cdcProperties.debezium.offsetStorageFileName)
            put("offset.flush.interval.ms", cdcProperties.debezium.offsetFlushIntervalMs.toString())
            
            put("database.hostname", cdcProperties.database.hostname)
            put("database.port", cdcProperties.database.port.toString())
            put("database.user", cdcProperties.database.username)
            put("database.password", cdcProperties.database.password)
            put("database.dbname", cdcProperties.database.name)
            put("database.server.id", cdcProperties.debezium.serverId)
            put("database.server.name", cdcProperties.debezium.serverName)
            
            put("table.include.list", "${cdcProperties.database.name}.order_outbox_events")
            
            put("column.include.list", 
                "${cdcProperties.database.name}.order_outbox_events.event_id," +
                "${cdcProperties.database.name}.order_outbox_events.aggregate_id," +
                "${cdcProperties.database.name}.order_outbox_events.aggregate_type," +
                "${cdcProperties.database.name}.order_outbox_events.event_type," +
                "${cdcProperties.database.name}.order_outbox_events.payload," +
                "${cdcProperties.database.name}.order_outbox_events.status," +
                "${cdcProperties.database.name}.order_outbox_events.created_at"
            )
            
            put("database.history", "io.debezium.relational.history.FileDatabaseHistory")
            put("database.history.file.filename", "/tmp/dbhistory.dat")
            
            put("snapshot.mode", cdcProperties.debezium.snapshotMode)
            put("poll.interval.ms", cdcProperties.debezium.pollIntervalMs.toString())
            put("max.batch.size", cdcProperties.debezium.maxBatchSize.toString())
            
            put("transforms", "outbox")
            put("transforms.outbox.type", "io.debezium.transforms.outbox.EventRouter")
            put("transforms.outbox.table.field.event.id", "event_id")
            put("transforms.outbox.table.field.event.key", "aggregate_id")
            put("transforms.outbox.table.field.event.type", "event_type")
            put("transforms.outbox.table.field.event.payload", "payload")
            put("transforms.outbox.route.by.field", "event_type")
            put("transforms.outbox.route.topic.replacement", "${cdcProperties.kafka.orderEventsTopic}.\${routedByValue}")
            
            put("include.schema.changes", "false")
            put("tombstones.on.delete", "false")
            put("decimal.handling.mode", "string")
        }
        
        return Configuration.from(props)
    }

    @Bean
    fun debeziumEngine(): DebeziumEngine<RecordChangeEvent<SourceRecord>> {
        val props = debeziumConfiguration().asProperties()
        
        return DebeziumEngine.create(ChangeEventFormat.of(Connect::class.java))
            .using(props)
            .notifying { records: List<RecordChangeEvent<SourceRecord>>, committer: DebeziumEngine.RecordCommitter<RecordChangeEvent<SourceRecord>> ->
                try {
                    records.forEach { record ->
                        handleRecord(record.record())
                    }
                    committer.markBatchFinished()
                } catch (e: Exception) {
                    logger.error("Error processing CDC records", e)
                }
            }
            .build()
    }
    
    private fun handleRecord(record: SourceRecord) {
        try {
            if (record.value() == null) {
                logger.debug("Skipping tombstone record")
                return
            }
            
            val value = record.value() as Struct
            val operation = value.getString("op")
            
            when (operation) {
                "c", "u" -> {
                    val after = value.getStruct("after")
                    if (after != null) {
                        orderOutboxConnector.processOutboxEvent(after)
                    }
                }
                "d" -> {
                    logger.debug("Delete operation detected, skipping for outbox pattern")
                }
                else -> {
                    logger.warn("Unknown operation: $operation")
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling record: ${e.message}", e)
        }
    }
}