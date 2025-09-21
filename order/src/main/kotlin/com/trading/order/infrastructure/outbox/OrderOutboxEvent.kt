package com.trading.order.infrastructure.outbox

import com.trading.common.outbox.EventTypes
import com.trading.common.outbox.OutboxEvent
import com.trading.common.util.UUIDv7Generator
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant


@Entity
@Table(
    name = "order_outbox_events",
    indexes = [
        Index(name = "idx_order_outbox_saga", columnList = "sagaId"),
        Index(name = "idx_order_outbox_created", columnList = "createdAt"),
        Index(name = "idx_order_outbox_order", columnList = "orderId"),
    ]
)
class OrderOutboxEvent(
    eventId: String = UUIDv7Generator.generate(),
    sagaId: String,
    eventType: String,

    @Column(nullable = false, length = 50)
    val orderId: String,

    @Column(nullable = false, length = 50)
    val userId: String,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false, length = 20)
    val orderType: String,  // LIMIT, MARKET

    @Column(nullable = false, length = 10)
    val side: String,  // BUY, SELL

    @Column(nullable = false, precision = 19, scale = 8)
    val quantity: BigDecimal,

    @Column(precision = 19, scale = 8)
    val price: BigDecimal? = null,  // null for MARKET orders

    createdAt: Instant = Instant.now()
) : OutboxEvent(
    eventId = eventId,
    sagaId = sagaId,
    eventType = eventType,
    createdAt = createdAt
) {

    companion object {
        fun create(
            sagaId: String,
            orderId: String,
            userId: String,
            symbol: String,
            orderType: String,
            side: String,
            quantity: BigDecimal,
            price: BigDecimal?,
        ): OrderOutboxEvent {
            return OrderOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Order.CREATED,
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                orderType = orderType,
                side = side,
                quantity = quantity,
                price = price
            )
        }


        fun createOrderCancelledEvent(
            sagaId: String,
            orderId: String,
            userId: String,
            symbol: String,
            orderType: String,
            side: String,
            quantity: BigDecimal,
            price: BigDecimal?,
        ): OrderOutboxEvent {
            return OrderOutboxEvent(
                sagaId = sagaId,
                eventType = EventTypes.Order.CANCELLED,
                orderId = orderId,
                userId = userId,
                symbol = symbol,
                orderType = orderType,
                side = side,
                quantity = quantity,
                price = price
            )
        }
    }
}