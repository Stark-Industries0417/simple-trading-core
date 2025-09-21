package com.trading.cdc.connector

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.dto.cdc.account.AccountCreatedDto
import com.trading.common.dto.cdc.account.AccountReservationFailedDto
import com.trading.common.dto.cdc.account.AccountUpdateFailedDto
import com.trading.common.outbox.EventTypes
import org.apache.kafka.connect.data.Struct
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class AccountOutboxConnector(
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

            logger.info("Processing account outbox event: operation=$operation, eventType=$eventType, status=$status")

            when (operation) {
                "c" -> {
                    // New event inserted - process if PENDING
                    if (status == "PENDING") {
                        when (eventType) {
                            EventTypes.Account.UPDATED -> processAccountUpdatedEvent(after)
                            EventTypes.Account.ROLLBACK -> processAccountRollbackEvent(after)
                            EventTypes.Account.RESERVATION_FAILED -> processAccountReservationFailedEvent(after)
                            EventTypes.Account.UPDATE_FAILED -> processAccountUpdateFailedEvent(after)
                            else -> logger.warn("Unknown event type: $eventType")
                        }
                    }
                }
                "u" -> {
                    // Event updated - process if status changed to FAILED
                    if (status == "FAILED" && eventType == EventTypes.Account.UPDATE_FAILED) {
                        logger.info("Processing FAILED status update for saga: {}", after.getString("saga_id"))
                        processAccountUpdateFailedEvent(after)
                    }
                }
                else -> {
                    logger.debug("Ignoring operation: $operation")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to process account outbox event", e)
            throw e
        }
    }

    private fun processAccountUpdatedEvent(record: Struct) {
        val event = mapToAccountCreatedDto(record)

        logger.info(
            "Publishing account updated event - TradeId: {}, BuyUser: {}, SellUser: {}, Symbol: {}, Amount: {}",
            event.tradeId,
            event.buyUserId,
            event.sellUserId,
            event.symbol,
            event.amount
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol  // Symbol 기반 파티셔닝으로 순서 보장

        kafkaTemplate.send("account.events", partitionKey, message)
    }

    private fun processAccountUpdateFailedEvent(record: Struct) {
        val event = mapToAccountUpdateFailedDto(record)

        logger.info(
            "Publishing account update failed event - TradeId: {}, Reason: {}, FailureType: {}",
            event.tradeId,
            event.reason,
            event.failureType
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol

        kafkaTemplate.send("account.events", partitionKey, message)
    }

    private fun processAccountRollbackEvent(record: Struct) {
        val event = mapToAccountCreatedDto(record)

        logger.info(
            "Publishing account rollback event - TradeId: {}, UserId: {}, Symbol: {}, Reason: {}",
            event.tradeId,
            event.buyUserId,
            event.symbol,
            event.reason
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol

        kafkaTemplate.send("account.events", partitionKey, message)
    }

    private fun processAccountReservationFailedEvent(record: Struct) {
        val event = mapToAccountReservationFailedDto(record)

        logger.info(
            "Publishing account reservation failed event - OrderId: {}, UserId: {}, Symbol: {}, FailureType: {}, Reason: {}",
            event.orderId,
            event.userId,
            event.symbol,
            event.failureType,
            event.reason
        )

        val message = objectMapper.writeValueAsString(event)
        val partitionKey = event.symbol.ifEmpty { "default" }  // Use default if symbol is empty

        kafkaTemplate.send("account.events", partitionKey, message)
    }

    private fun mapToAccountCreatedDto(record: Struct): AccountCreatedDto {
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

        // Boolean 변환 (기본값 false)
        fun extractBoolean(fieldName: String, defaultValue: Boolean = false): Boolean {
            return try {
                record.getBoolean(fieldName)
            } catch (e: Exception) {
                defaultValue
            }
        }

        return AccountCreatedDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            tradeId = record.getString("trade_id"),
            orderId = record.getString("order_id"),
            buyUserId = record.getString("buy_user_id"),
            sellUserId = record.getString("sell_user_id"),
            symbol = record.getString("symbol"),
            amount = extractBigDecimal("amount"),
            quantity = extractBigDecimal("quantity"),
            buyerNewBalance = extractNullableBigDecimal("buyer_new_balance"),
            sellerNewBalance = extractNullableBigDecimal("seller_new_balance"),
            failureType = extractNullableString("failure_type"),
            reason = extractNullableString("reason"),
            shouldRetry = extractBoolean("should_retry"),
            partitionKey = record.getString("partition_key"),
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            errorMessage = extractNullableString("error_message"),
            retryCount = extractInt("retry_count"),
            createdAt = record.getString("created_at")
        )
    }

    private fun mapToAccountUpdateFailedDto(record: Struct): AccountUpdateFailedDto {
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

        // Boolean 변환 (기본값 false)
        fun extractBoolean(fieldName: String, defaultValue: Boolean = false): Boolean {
            return try {
                record.getBoolean(fieldName)
            } catch (e: Exception) {
                defaultValue
            }
        }

        return AccountUpdateFailedDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            tradeId = record.getString("trade_id"),
            orderId = record.getString("order_id"),
            buyUserId = record.getString("buy_user_id"),
            sellUserId = record.getString("sell_user_id"),
            symbol = record.getString("symbol"),
            amount = extractBigDecimal("amount"),
            quantity = extractBigDecimal("quantity"),
            failureType = record.getString("failure_type"),
            reason = record.getString("reason"),
            shouldRetry = extractBoolean("should_retry"),
            status = record.getString("status"),
            processedAt = extractNullableString("processed_at"),
            errorMessage = extractNullableString("error_message"),
            retryCount = extractInt("retry_count"),
            createdAt = record.getString("created_at")
        )
    }

    private fun mapToAccountReservationFailedDto(record: Struct): AccountReservationFailedDto {
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

        // Boolean 변환 (기본값 false)
        fun extractBoolean(fieldName: String, defaultValue: Boolean = false): Boolean {
            return try {
                record.getBoolean(fieldName)
            } catch (e: Exception) {
                defaultValue
            }
        }

        return AccountReservationFailedDto(
            eventId = record.getString("event_id"),
            sagaId = record.getString("saga_id"),
            eventType = record.getString("event_type"),
            orderId = record.getString("order_id"),
            userId = record.getString("buy_user_id"),  // userId is stored in buy_user_id field
            symbol = record.getString("symbol"),
            quantity = extractBigDecimal("quantity"),
            failureType = record.getString("failure_type"),
            reason = record.getString("reason") ?: "",
            shouldRetry = extractBoolean("should_retry"),
            createdAt = record.getString("created_at")
        )
    }


    override fun getConnectorName(): String {
        return "AccountOutboxConnector"
    }

    override fun getSourceTable(): String {
        return "account_outbox_events"
    }
}