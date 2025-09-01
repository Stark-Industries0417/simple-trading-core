package com.trading.common.event.account

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal
import java.time.Instant


data class AccountUpdateFailedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val userId: String,
    val relatedTradeId: String,
    val failureReason: String,
    val amount: BigDecimal,
    val shouldRetry: Boolean = false
) : DomainEvent
