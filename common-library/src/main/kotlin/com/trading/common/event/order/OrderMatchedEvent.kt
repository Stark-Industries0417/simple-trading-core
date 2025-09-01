package com.trading.common.event.order

import com.trading.common.dto.trade.TradeDTO
import com.trading.common.event.base.DomainEvent
import java.time.Instant


data class OrderMatchedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val trade: TradeDTO
) : DomainEvent
