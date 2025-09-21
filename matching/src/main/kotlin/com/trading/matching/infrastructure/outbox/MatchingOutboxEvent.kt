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
    val quantity: BigDecimal,

    @Column(nullable = true, precision = 19, scale = 8)
    val price: BigDecimal? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: MatchingStatus = MatchingStatus.PENDING,

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
            quantity: BigDecimal,
            price: BigDecimal,
        ): MatchingOutboxEvent {
            return MatchingOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Trade.CREATED,
                buyOrderId = buyOrderId,
                sellOrderId = sellOrderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                quantity = quantity,
                price = price
            )
        }

        fun createNoMatchEvent(
            sagaId: String,
            orderId: String,
            userId: String,
            symbol: String,
            orderQuantity: BigDecimal,
            orderPrice: BigDecimal
        ): MatchingOutboxEvent {
            return MatchingOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Trade.NO_MATCH,  // 매칭 없음을 명시
                buyOrderId = orderId,
                sellOrderId = "",
                buyUserId = userId,
                sellUserId = "",
                symbol = symbol,
                quantity = BigDecimal.ZERO,  // 매칭된 수량 0
                price = orderPrice,
                status = MatchingStatus.PROCESSED  // 처리는 완료됨
            )
        }

        fun createMatchingFailedEvent(
            sagaId: String,
            buyOrderId: String,
            sellOrderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal? = null,
        ): MatchingOutboxEvent {
            return MatchingOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Trade.FAILED,
                buyOrderId = buyOrderId,
                sellOrderId = sellOrderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                quantity = quantity,
                price = price
            )
        }
    }

    fun markAsProcessed() {
        this.status = MatchingStatus.PROCESSED
        this.processedAt = Instant.now()
    }

    fun markAsFailed(error: String) {
        this.status = MatchingStatus.FAILED
        this.errorMessage = error
        this.retryCount++
    }

    fun markForRetry() {
        this.status = MatchingStatus.RETRY
        this.retryCount++
    }
}

enum class MatchingStatus {
    PENDING,    // 처리 대기중
    PROCESSED,  // CDC에 의해 처리됨
    FAILED,     // 처리 실패
    RETRY       // 재시도 필요
}