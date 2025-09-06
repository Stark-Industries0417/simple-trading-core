package com.trading.order.infrastructure.outbox

import com.trading.common.outbox.OutboxEvent
import jakarta.persistence.*

@Entity
@Table(
    name = "order_outbox_events",
    indexes = [
        Index(name = "idx_outbox_status", columnList = "status"),
        Index(name = "idx_outbox_created", columnList = "createdAt"),
        Index(name = "idx_outbox_aggregate", columnList = "aggregateId"),
        Index(name = "idx_outbox_saga", columnList = "sagaId")
    ]
)
class OrderOutboxEvent(
    eventId: String,
    aggregateId: String,
    eventType: String,
    payload: String,
    
    @Column(nullable = false)
    val orderId: String,
    
    @Column(nullable = false)
    val userId: String,
    
    @Column(nullable = true)
    val sagaId: String? = null,
    
    @Column(nullable = true)
    val tradeId: String? = null,
    
    @Column(nullable = false)
    val topic: String = "order.events"
) : OutboxEvent(
    eventId = eventId,
    aggregateId = aggregateId,
    aggregateType = "Order",
    eventType = eventType,
    payload = payload
)