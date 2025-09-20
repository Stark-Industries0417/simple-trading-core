package com.trading.common.dto.cdc.matching

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal

/**
 * CDC가 Matching Outbox 테이블에서 읽어서 Kafka로 발행하는 이벤트
 *
 * matching_outbox_events 테이블과 동일한 필드 구조
 */
data class MatchingCreatedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val tradeId: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val status: String,              // MatchingStatus (PENDING, PROCESSED, FAILED, RETRY)
    val processedAt: String?,
    val errorMessage: String?,       // 에러 메시지 (nullable)
    val retryCount: Int,             // 재시도 횟수
    val createdAt: String,
    val partitionKey: String?        // Kafka 파티션 키 (nullable)
) : DomainEvent