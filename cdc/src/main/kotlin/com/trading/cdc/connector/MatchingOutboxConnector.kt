package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.matching.MatchingCreatedDto
import com.trading.common.dto.cdc.matching.MatchingFailedDto
import com.trading.common.dto.cdc.matching.MatchingNoMatchDto
import com.trading.common.outbox.EventTypes
import org.apache.kafka.connect.data.Struct
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal


@Component
class MatchingOutboxConnector(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : CdcConnector {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun processEvent(record: Struct) {
        try {
            val operation = record.getString("op")
            val after = record.getStruct("after")

            if (after == null) {
                logger.debug("No 'after' data in record")
                return
            }

            val eventType = after.getString("event_type")
            val status = after.getString("status")

            logger.info("Processing matching outbox event: operation=$operation, eventType=$eventType, status=$status")

            when (operation) {
                "c" -> {
                    // New event inserted - process if PENDING
                    if (status == "PENDING") {
                        when (eventType) {
                            EventTypes.Trade.CREATED -> processTradeCreatedEvent(after)
                            EventTypes.Trade.NO_MATCH -> processTradeNoMatchEvent(after)
                            EventTypes.Trade.FAILED -> processTradeFailedEvent(after)
                            else -> logger.warn("Unknown event type: $eventType")
                        }
                    }
                }
                "u" -> {
                    // Event updated - process if status changed to FAILED
                    if (status == "FAILED" && eventType == EventTypes.Trade.FAILED) {
                        logger.info("Processing FAILED status update for saga: {}", after.getString("saga_id"))
                        processTradeFailedEvent(after)
                    }
                }
                else -> {
                    logger.debug("Ignoring operation: $operation")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process matching outbox event: ${e.message}", e)
        }
    }

    private fun processTradeCreatedEvent(record: Struct) {
        val event = mapToMatchingCreatedDto(record)

        logger.info(
            "Publishing trade created event - TradeId: {}, Symbol: {}, Quantity: {}, Price: {}",
            event.tradeId,
            event.symbol,
            event.quantity,
            event.price
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol

        kafkaTemplate.send("trade.events", partitionKey, message)
    }

    private fun processTradeFailedEvent(record: Struct) {
        val event = mapToMatchingFailedDto(record)

        logger.info(
            "Publishing trade failed event - SagaId: {}, BuyOrderId: {}, Symbol: {}",
            event.sagaId,
            event.buyOrderId,
            event.symbol
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol

        kafkaTemplate.send("trade.events", partitionKey, message)
    }

    private fun processTradeNoMatchEvent(record: Struct) {
        val event = mapToMatchingNoMatchDto(record)

        logger.info(
            "Publishing trade no-match event - SagaId: {}, OrderId: {}, Symbol: {}, Price: {}",
            event.sagaId,
            event.orderId,
            event.symbol,
            event.price
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol

        kafkaTemplate.send("trade.events", partitionKey, message)
    }

    private fun mapToMatchingCreatedDto(record: Struct): MatchingCreatedDto {
        // BigDecimal 변환 헬퍼 함수
        fun extractBigDecimal(fieldName: String): BigDecimal {
            return try {
                val value = record.getString(fieldName)
                BigDecimal(value)
            } catch (e: Exception) {
                logger.warn("Failed to parse $fieldName as BigDecimal, defaulting to 0")
                BigDecimal.ZERO
            }
        }

        fun extractNullableBigDecimal(fieldName: String): BigDecimal {
            return try {
                val value = record.getString(fieldName)
                if (value.isNullOrBlank())
                    BigDecimal.ZERO
                else BigDecimal(value)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }

        // nullable String 변환
        fun extractNullableString(fieldName: String): String? {
            return try {
                record.getString(fieldName)
            } catch (e: Exception) {
                null
            }
        }

        // Integer 변환 (기본값 0)
        fun extractInt(fieldName: String, defaultValue: Int = 0): Int {
            return try {
                record.getInt32(fieldName)
            } catch (e: Exception) {
                defaultValue
            }
        }

        return MatchingCreatedDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            tradeId = record.getString("trade_id"),
            buyOrderId = record.getString("buy_order_id"),
            sellOrderId = record.getString("sell_order_id"),
            buyUserId = record.getString("buy_user_id"),
            sellUserId = record.getString("sell_user_id"),
            symbol = record.getString("symbol"),
            quantity = extractBigDecimal("quantity"),
            price = extractNullableBigDecimal("price"),
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            errorMessage = extractNullableString("error_message"),
            retryCount = extractInt("retry_count"),
            createdAt = record.getString("created_at"),
        )
    }

    private fun mapToMatchingFailedDto(record: Struct): MatchingFailedDto {
        fun extractBigDecimal(fieldName: String): BigDecimal {
            return try {
                val value = record.getString(fieldName)
                BigDecimal(value)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }

        fun extractNullableBigDecimal(fieldName: String): BigDecimal? {
            return try {
                val value = record.getString(fieldName)
                if (value.isNullOrBlank()) null else BigDecimal(value)
            } catch (e: Exception) {
                null
            }
        }

        fun extractNullableString(fieldName: String): String? {
            return try {
                record.getString(fieldName)
            } catch (e: Exception) {
                null
            }
        }

        fun extractInt(fieldName: String, defaultValue: Int = 0): Int {
            return try {
                record.getInt32(fieldName)
            } catch (e: Exception) {
                defaultValue
            }
        }

        return MatchingFailedDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            buyOrderId = record.getString("buy_order_id"),
            sellOrderId = record.getString("sell_order_id"),
            buyUserId = record.getString("buy_user_id"),
            sellUserId = record.getString("sell_user_id"),
            symbol = record.getString("symbol"),
            quantity = extractBigDecimal("quantity"),
            price = extractNullableBigDecimal("price"),
            tradeId = record.getString("trade_id"),
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            errorMessage = extractNullableString("error_message"),
            retryCount = extractInt("retry_count"),
            createdAt = record.getString("created_at")
        )
    }

    private fun mapToMatchingNoMatchDto(record: Struct): MatchingNoMatchDto {
        // Helper functions
        fun extractBigDecimal(fieldName: String): BigDecimal {
            return try {
                val value = record.getString(fieldName)
                BigDecimal(value)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }

        fun extractNullableBigDecimal(fieldName: String): BigDecimal? {
            return try {
                val value = record.getString(fieldName)
                if (value.isNullOrBlank()) null else BigDecimal(value)
            } catch (e: Exception) {
                null
            }
        }

        fun extractNullableString(fieldName: String): String? {
            return try {
                record.getString(fieldName)
            } catch (e: Exception) {
                null
            }
        }

        // Determine order side (assuming buy_user_id is populated for buy orders)
        val side = if (record.getString("buy_user_id").isNotEmpty()) "BUY" else "SELL"

        return MatchingNoMatchDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            orderId = record.getString("buy_order_id"),
            userId = record.getString("buy_user_id"),
            symbol = record.getString("symbol"),
            orderType = "LIMIT", // NO_MATCH는 지정가 주문에만 발생
            side = side,
            quantity = extractBigDecimal("quantity"),
            price = extractNullableBigDecimal("price") ?: BigDecimal.ZERO,
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            createdAt = record.getString("created_at"),
            metadata = null
        )
    }


    override fun getConnectorName(): String {
        return "MatchingOutboxConnector"
    }

    override fun getSourceTable(): String {
        return "matching_outbox_events"
    }
}