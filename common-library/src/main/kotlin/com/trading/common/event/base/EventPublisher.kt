package com.trading.common.event.base


interface EventPublisher {

    fun publish(event: DomainEvent)

    fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}
