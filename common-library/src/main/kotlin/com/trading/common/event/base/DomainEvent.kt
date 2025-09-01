package com.trading.common.event.base
import java.time.Instant


interface DomainEvent {
    val eventId: String
    val aggregateId: String
    val occurredAt: Instant
    val traceId: String
}
