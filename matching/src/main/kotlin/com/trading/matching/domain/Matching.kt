package com.trading.matching.domain

import com.trading.common.dto.order.OrderSide
import com.trading.common.util.UUIDv7Generator
import com.trading.matching.infrastructure.outbox.MatchingOutboxEvent
import com.trading.matching.infrastructure.outbox.MatchingStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "matchings",
    indexes = [
        Index(name = "idx_matching_trade_id", columnList = "tradeId"),
        Index(name = "idx_matching_buy_order", columnList = "buyOrderId"),
        Index(name = "idx_matching_sell_order", columnList = "sellOrderId"),
        Index(name = "idx_matching_symbol", columnList = "symbol"),
        Index(name = "idx_matching_status", columnList = "status"),
        Index(name = "idx_matching_created", columnList = "createdAt")
    ]
)
class Matching private constructor(
    @Id
    @Column(length = 50)
    val id: String,

    @Column(nullable = false, length = 50, unique = true)
    val tradeId: String,

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

    @Column(nullable = false, precision = 19, scale = 2)
    val price: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MatchingStatus = MatchingStatus.PENDING,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column
    var processedAt: Instant? = null,

    @Column(length = 500)
    var errorMessage: String? = null,

    @Column
    var retryCount: Int = 0,

    @Version
    var version: Long = 0
) {
    companion object {
        fun create(
            buyOrderId: String,
            sellOrderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            quantity: BigDecimal,
            price: BigDecimal,
            uuidGenerator: UUIDv7Generator
        ): Matching {
            require(buyOrderId.isNotBlank()) { "Buy order ID cannot be blank" }
            require(sellOrderId.isNotBlank()) { "Sell order ID cannot be blank" }
            require(buyUserId.isNotBlank()) { "Buy user ID cannot be blank" }
            require(sellUserId.isNotBlank()) { "Sell user ID cannot be blank" }
            require(symbol.isNotBlank()) { "Symbol cannot be blank" }
            require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
            require(price > BigDecimal.ZERO) { "Price must be positive" }

            val tradeId = uuidGenerator.generateTradeId()

            return Matching(
                id = uuidGenerator.generateEventId(),
                tradeId = tradeId,
                buyOrderId = buyOrderId,
                sellOrderId = sellOrderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol.uppercase(),
                quantity = quantity,
                price = price
            )
        }

        /**
         * 체결되지 않은 주문이나 실패한 주문을 위한 팩토리 메서드
         * 한쪽만 주문 정보가 있는 경우 사용 (예: 시장가 주문 실패, 지정가 주문 대기)
         */
        fun createUnmatched(
            orderId: String,
            userId: String,
            symbol: String,
            side: OrderSide,
            quantity: BigDecimal,
            price: BigDecimal,
            uuidGenerator: UUIDv7Generator,
            status: MatchingStatus = MatchingStatus.PENDING
        ): Matching {
            require(orderId.isNotBlank()) { "Order ID cannot be blank" }
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(symbol.isNotBlank()) { "Symbol cannot be blank" }
            require(quantity > BigDecimal.ZERO) { "Quantity must be positive" }
            require(price >= BigDecimal.ZERO) { "Price cannot be negative" }

            val tradeId = uuidGenerator.generateTradeId()

            // 체결되지 않은 주문의 경우 반대편을 "UNMATCHED"로 표시
            val unmatchedPlaceholder = "UNMATCHED"

            return Matching(
                id = uuidGenerator.generateEventId(),
                tradeId = tradeId,
                buyOrderId = if (side == OrderSide.BUY) orderId else unmatchedPlaceholder,
                sellOrderId = if (side == OrderSide.SELL) orderId else unmatchedPlaceholder,
                buyUserId = if (side == OrderSide.BUY) userId else unmatchedPlaceholder,
                sellUserId = if (side == OrderSide.SELL) userId else unmatchedPlaceholder,
                symbol = symbol.uppercase(),
                quantity = quantity,
                price = price,
                status = status
            )
        }
    }

    fun markAsProcessed(): Matching {
        require(status == MatchingStatus.PENDING) {
            "Can only process pending matchings"
        }

        return this.apply {
            status = MatchingStatus.PROCESSED
            processedAt = Instant.now()
            updatedAt = Instant.now()
        }
    }

    fun markAsFailed(error: String): Matching {
        require(status in listOf(MatchingStatus.PENDING, MatchingStatus.RETRY)) {
            "Can only fail pending or retry matchings"
        }

        return this.apply {
            status = MatchingStatus.FAILED
            errorMessage = error
            retryCount++
            updatedAt = Instant.now()
        }
    }

    fun markForRetry(): Matching {
        require(status in listOf(MatchingStatus.PENDING, MatchingStatus.FAILED)) {
            "Can only retry pending or failed matchings"
        }
        require(retryCount < 3) {
            "Maximum retry count exceeded"
        }

        return this.apply {
            status = MatchingStatus.RETRY
            retryCount++
            updatedAt = Instant.now()
        }
    }

    fun complete(): Matching {
        require(status == MatchingStatus.PROCESSED) {
            "Can only complete processed matchings"
        }

        return this.apply {
            status = MatchingStatus.PROCESSED
            processedAt = Instant.now()
            updatedAt = Instant.now()
        }
    }

    fun toOutboxEvent(sagaId: String): MatchingOutboxEvent {
        return MatchingOutboxEvent.createMatchedEvent(
            sagaId = sagaId,
            buyOrderId = buyOrderId,
            sellOrderId = sellOrderId,
            buyUserId = buyUserId,
            sellUserId = sellUserId,
            symbol = symbol,
            quantity = quantity,
            price = price,
            tradeId = tradeId
        )
    }

    fun toFailedOutboxEvent(sagaId: String): MatchingOutboxEvent {
        return MatchingOutboxEvent.createMatchingFailedEvent(
            sagaId = sagaId,
            buyOrderId = buyOrderId,
            sellOrderId = sellOrderId,
            buyUserId = buyUserId,
            sellUserId = sellUserId,
            symbol = symbol,
            quantity = quantity,
            price = price,
            tradeId = tradeId
        )
    }

    fun isPending(): Boolean = status == MatchingStatus.PENDING

    fun isProcessed(): Boolean = status == MatchingStatus.PROCESSED

    fun isFailed(): Boolean = status == MatchingStatus.FAILED

    fun canRetry(): Boolean = status in listOf(MatchingStatus.PENDING, MatchingStatus.FAILED) && retryCount < 3

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matching) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Matching(id='$id', tradeId='$tradeId', buyOrderId='$buyOrderId', " +
                "sellOrderId='$sellOrderId', symbol='$symbol', quantity=$quantity, " +
                "price=$price, status=$status, retryCount=$retryCount)"
    }
}