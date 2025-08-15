package com.trading.common.event
import com.trading.common.dto.MarketDataDTO
import java.time.Instant
data class MarketDataUpdatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val marketData: MarketDataDTO
) : DomainEvent
