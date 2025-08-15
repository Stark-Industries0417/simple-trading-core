package com.trading.common.event
import java.time.Instant
data class AccountUpdateFailedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val userId: String,
    val relatedTradeId: String,
    val failureReason: String,
    val shouldRetry: Boolean = false
) : DomainEvent
