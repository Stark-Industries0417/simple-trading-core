package com.trading.common.event
import com.trading.common.dto.TradeDTO
import java.time.Instant
data class OrderMatchedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val trade: TradeDTO
) : DomainEvent
