package com.trading.cdc.util

import org.apache.kafka.connect.data.Struct
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

/**
 * Debezium 데이터 변환을 위한 유틸리티 클래스
 * MySQL 타입을 Kafka Connect 타입으로 변환
 */
object DebeziumHelper {
    private val logger = LoggerFactory.getLogger(DebeziumHelper::class.java)

    /**
     * BigDecimal 변환 (기본값: 0)
     */
    fun extractBigDecimal(record: Struct, fieldName: String): BigDecimal {
        return try {
            val value = record.getString(fieldName)
            BigDecimal(value)
        } catch (e: Exception) {
            logger.warn("Failed to parse $fieldName as BigDecimal, defaulting to 0")
            BigDecimal.ZERO
        }
    }

    /**
     * Nullable BigDecimal 변환
     */
    fun extractNullableBigDecimal(record: Struct, fieldName: String): BigDecimal? {
        return try {
            val value = record.getString(fieldName)
            if (value.isNullOrBlank()) null else BigDecimal(value)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Nullable String 변환
     */
    fun extractNullableString(record: Struct, fieldName: String): String? {
        return try {
            record.getString(fieldName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Integer 변환 (기본값 제공)
     */
    fun extractInt(record: Struct, fieldName: String, defaultValue: Int = 0): Int {
        return try {
            record.getInt32(fieldName)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Boolean 변환 (기본값: false)
     */
    fun extractBoolean(record: Struct, fieldName: String, defaultValue: Boolean = false): Boolean {
        return try {
            record.getBoolean(fieldName)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Timestamp 변환 - Debezium이 MySQL TIMESTAMP를 Long (milliseconds)로 변환
     *
     * MySQL TIMESTAMP(6) -> Debezium Long (milliseconds since epoch) -> String (ISO-8601)
     */
    fun extractTimestamp(record: Struct, fieldName: String): String {
        return try {
            val timestampMillis = record.getInt64(fieldName)
            Instant.ofEpochMilli(timestampMillis).toString()
        } catch (e: Exception) {
            logger.warn("Failed to parse $fieldName as timestamp, using current time", e)
            Instant.now().toString()
        }
    }

    /**
     * Nullable Timestamp 변환
     */
    fun extractNullableTimestamp(record: Struct, fieldName: String): String? {
        return try {
            val timestampMillis = record.getInt64(fieldName)
            Instant.ofEpochMilli(timestampMillis).toString()
        } catch (e: Exception) {
            null
        }
    }
}