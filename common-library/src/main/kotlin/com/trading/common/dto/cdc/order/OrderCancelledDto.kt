package com.trading.common.dto.cdc.order

import com.trading.common.dto.order.OrderSide
import com.trading.common.dto.order.OrderType
import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal
import java.time.Instant


data class OrderCancelledDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val orderId: String,
    val userId: String,
    val symbol: String,
    val orderType: OrderType,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val createdAt: String,
) : DomainEvent
