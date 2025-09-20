package com.trading.matching.infrastructure.outbox

import com.trading.common.outbox.EventTypes
import com.trading.common.outbox.OutboxEvent
import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant


@Entity
@Table(
    name = "matching_outbox_events",
    indexes = [
        Index(name = "idx_matching_outbox_saga", columnList = "sagaId"),
        Index(name = "idx_matching_outbox_created", columnList = "createdAt"),
        Index(name = "idx_matching_outbox_trade", columnList = "tradeId"),
        Index(name = "idx_matching_outbox_status", columnList = "status"),
        Index(name = "idx_matching_outbox_partition", columnList = "partitionKey")
    ]
)
class MatchingOutboxEvent(
    eventId: String = UUIDv7Generator.generate(),
    sagaId: String,
    eventType: String,

    @Column(nullable = false, length = 50)
    val buyOrderId: String,

    @Column(nullable = false, length = 50)
    val sellOrderId: String,

    @Column(nullable = false, length = 50)
    val buyUserId: String,

    @Column(nullable = false, length = 50)
    val sellUserId: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, precision = 19, scale = 8)
    val matchedQuantity: BigDecimal,

    @Column(nullable = true, precision = 19, scale = 8)
    val matchedPrice: BigDecimal? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column
    var processedAt: Instant? = null,

    @Column(length = 500)
    var errorMessage: String? = null,

    @Column
    var retryCount: Int = 0,

    createdAt: Instant = Instant.now()
) : OutboxEvent(
    eventId = eventId,
    sagaId = sagaId,
    eventType = eventType,
    createdAt = createdAt
) {

    companion object {
        fun createMatchedEvent(
            sagaId: String,
            buyOrderId: String,
            sellOrderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            matchedQuantity: BigDecimal,
            matchedPrice: BigDecimal,
        ): MatchingOutboxEvent {
            return MatchingOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Trade.CREATED,
                buyOrderId = buyOrderId,
                sellOrderId = sellOrderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                matchedQuantity = matchedQuantity,
                matchedPrice = matchedPrice
            )
        }

        fun createMatchingFailedEvent(
            sagaId: String,
            buyOrderId: String,
            sellOrderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            matchedQuantity: BigDecimal,
            matchedPrice: BigDecimal? = null,
        ): MatchingOutboxEvent {
            return MatchingOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Trade.FAILED,
                buyOrderId = buyOrderId,
                sellOrderId = sellOrderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                matchedQuantity = matchedQuantity,
                matchedPrice = matchedPrice
            )
        }
    }

    fun markAsProcessed() {
        this.status = OutboxStatus.PROCESSED
        this.processedAt = Instant.now()
    }

    fun markAsFailed(error: String) {
        this.status = OutboxStatus.FAILED
        this.errorMessage = error
        this.retryCount++
    }

    fun markForRetry() {
        this.status = OutboxStatus.RETRY
        this.retryCount++
    }
}

enum class OutboxStatus {
    PENDING,    // 처리 대기중
    PROCESSED,  // CDC에 의해 처리됨
    FAILED,     // 처리 실패
    RETRY       // 재시도 필요
}