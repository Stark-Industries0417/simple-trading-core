package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.time.Instant

data class TradeFailedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val orderId: String,
    val symbol: String,
    val reason: String,
    val shouldRetry: Boolean = false,
    val metadata: Map<String, Any>? = null
) : DomainEvent