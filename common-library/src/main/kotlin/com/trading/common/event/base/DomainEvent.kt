package com.trading.common.event.base
import java.time.Instant


interface DomainEvent {
    val eventId: String
    val sagaId: String
}
