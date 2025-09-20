package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.matching.MatchingCreatedDto
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
            val operation = extractOperation(record)

            when (operation) {
                "c"-> {
                    val after = record.getStruct("after")
                    val eventType = after.getString("event_type")

                    val status = after.getString("status")
                    if (status != "PENDING") {
                        logger.debug("Skipping non-pending event: $eventType with status: $status")
                        return
                    }

                    when (eventType) {
                        EventTypes.Trade.CREATED -> processTradeCreatedEvent(after)
                        EventTypes.Trade.FAILED -> processTradeFailedEvent(after)
                        else -> logger.warn("Unknown event type: $eventType")
                    }
                }
                "d" -> {
                    logger.debug("Delete operation ignored for matching outbox")
                }
                else -> {
                    logger.warn("Unknown operation: $operation")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process matching outbox event", e)
            throw e
        }
    }

    private fun processTradeCreatedEvent(record: Struct) {
        val event = mapToMatchingCreatedDto(record)

        logger.info(
            "Publishing trade created event - TradeId: {}, Symbol: {}, Quantity: {}, Price: {}",
            event.tradeId,
            event.symbol,
            event.matchedQuantity,
            event.matchedPrice
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol  // Symbol 기반 파티셔닝으로 순서 보장

        kafkaTemplate.send("trade.events", partitionKey, message)
    }

    private fun processTradeFailedEvent(record: Struct) {
        val event = mapToMatchingCreatedDto(record) // Failed 이벤트도 같은 DTO 구조 사용

        logger.info(
            "Publishing trade failed event - OrderId: {}, Symbol: {}",
            event.buyOrderId,
            event.symbol
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

        // nullable BigDecimal 변환
        fun extractNullableBigDecimal(fieldName: String): BigDecimal? {
            return try {
                val value = record.getString(fieldName)
                if (value.isNullOrBlank()) null else BigDecimal(value)
            } catch (e: Exception) {
                null
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
            matchedQuantity = extractBigDecimal("matched_quantity"),
            matchedPrice = extractNullableBigDecimal("matched_price"),
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            errorMessage = extractNullableString("error_message"),
            retryCount = extractInt("retry_count"),
            createdAt = record.getString("created_at"),
            partitionKey = extractNullableString("partition_key")
        )
    }

    private fun extractOperation(record: Struct): String {
        return record.getString("op")
    }

    override fun getConnectorName(): String {
        return "MatchingOutboxConnector"
    }

    override fun getSourceTable(): String {
        return "matching_outbox_events"
    }
}