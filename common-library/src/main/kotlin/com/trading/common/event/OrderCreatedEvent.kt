package com.trading.common.event
import com.trading.common.dto.OrderDTO
import java.time.Instant
data class OrderCreatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val order: OrderDTO
) : DomainEvent
