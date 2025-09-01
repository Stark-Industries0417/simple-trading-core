package com.trading.common.event.market

import com.trading.common.dto.market.MarketDataDTO
import com.trading.common.event.base.DomainEvent
import java.time.Instant

data class MarketDataUpdatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val marketData: MarketDataDTO
) : DomainEvent
