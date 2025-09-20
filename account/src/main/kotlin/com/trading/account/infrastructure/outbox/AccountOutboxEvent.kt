package com.trading.account.infrastructure.outbox

import com.trading.common.outbox.EventTypes
import com.trading.common.outbox.OutboxEvent
import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "account_outbox_events",
    indexes = [
        Index(name = "idx_account_outbox_saga", columnList = "sagaId"),
        Index(name = "idx_account_outbox_created", columnList = "createdAt"),
        Index(name = "idx_account_outbox_trade", columnList = "tradeId"),
        Index(name = "idx_account_outbox_status", columnList = "status"),
        Index(name = "idx_account_outbox_partition", columnList = "partitionKey")
    ]
)
class AccountOutboxEvent(
    eventId: String = UUIDv7Generator.generate(),
    sagaId: String,
    eventType: String,

    @Column(nullable = false, length = 50)
    val tradeId: String,

    @Column(nullable = false, length = 50)
    val orderId: String,

    @Column(nullable = false, length = 50)
    val buyUserId: String,

    @Column(nullable = false, length = 50)
    val sellUserId: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, precision = 19, scale = 8)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 19, scale = 8)
    val quantity: BigDecimal,

    @Column(nullable = true, precision = 19, scale = 8)
    val buyerNewBalance: BigDecimal? = null,

    @Column(nullable = true, precision = 19, scale = 8)
    val sellerNewBalance: BigDecimal? = null,

    @Column(nullable = true, length = 50)
    val failureType: String? = null,

    @Column(nullable = true, length = 500)
    val reason: String? = null,

    @Column(nullable = false)
    val shouldRetry: Boolean = false,

    @Column(nullable = false, length = 50)
    val partitionKey: String = symbol,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: AccountOutboxStatus = AccountOutboxStatus.PENDING,

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
        fun createAccountUpdatedEvent(
            sagaId: String,
            tradeId: String,
            orderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            amount: BigDecimal,
            quantity: BigDecimal,
            buyerNewBalance: BigDecimal,
            sellerNewBalance: BigDecimal
        ): AccountOutboxEvent {
            return AccountOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Account.UPDATED,
                tradeId = tradeId,
                orderId = orderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                amount = amount,
                quantity = quantity,
                buyerNewBalance = buyerNewBalance,
                sellerNewBalance = sellerNewBalance
            )
        }

        fun createAccountUpdateFailedEvent(
            sagaId: String,
            tradeId: String,
            orderId: String,
            buyUserId: String,
            sellUserId: String,
            symbol: String,
            amount: BigDecimal,
            quantity: BigDecimal,
            reason: String,
            failureType: String,
            shouldRetry: Boolean
        ): AccountOutboxEvent {
            return AccountOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Account.UPDATE_FAILED,
                tradeId = tradeId,
                orderId = orderId,
                buyUserId = buyUserId,
                sellUserId = sellUserId,
                symbol = symbol,
                amount = amount,
                quantity = quantity,
                reason = reason,
                failureType = failureType,
                shouldRetry = shouldRetry
            )
        }

        fun createAccountRollbackEvent(
            sagaId: String,
            tradeId: String,
            orderId: String,
            userId: String,
            symbol: String,
            amount: BigDecimal,
            quantity: BigDecimal,
            rollbackType: String,
            success: Boolean,
            reason: String
        ): AccountOutboxEvent {
            return AccountOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Account.ROLLBACK,
                tradeId = tradeId,
                orderId = orderId,
                buyUserId = userId,
                sellUserId = "",
                symbol = symbol,
                amount = amount,
                quantity = quantity,
                reason = reason,
                failureType = if (!success) rollbackType else null,
                shouldRetry = false
            )
        }
    }

    fun markAsProcessed() {
        this.status = AccountOutboxStatus.PROCESSED
        this.processedAt = Instant.now()
    }

    fun markAsFailed(error: String) {
        this.status = AccountOutboxStatus.FAILED
        this.errorMessage = error
        this.retryCount++
    }

    fun markForRetry() {
        this.status = AccountOutboxStatus.RETRY
        this.retryCount++
    }
}

enum class AccountOutboxStatus {
    PENDING,    // 처리 대기중
    PROCESSED,  // CDC에 의해 처리됨
    FAILED,     // 처리 실패
    RETRY       // 재시도 필요
}