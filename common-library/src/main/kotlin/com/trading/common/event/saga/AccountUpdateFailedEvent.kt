package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.time.Instant

data class AccountUpdateFailedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val tradeId: String,
    val orderId: String,
    val buyUserId: String? = null,
    val sellUserId: String? = null,
    val reason: String,
    val failureType: FailureType,
    val shouldRetry: Boolean = false,
    val metadata: Map<String, Any>? = null
) : DomainEvent {
    enum class FailureType {
        INSUFFICIENT_BALANCE,
        INSUFFICIENT_SHARES,
        LOCK_TIMEOUT,
        TECHNICAL_ERROR,
        VALIDATION_ERROR
    }
}