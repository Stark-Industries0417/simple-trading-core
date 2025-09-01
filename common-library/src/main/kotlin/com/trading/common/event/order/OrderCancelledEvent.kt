package com.trading.common.event.order

import com.trading.common.event.base.DomainEvent
import java.time.Instant


data class OrderCancelledEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val orderId: String,
    val userId: String,
    val reason: String
) : DomainEvent
