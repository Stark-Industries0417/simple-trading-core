package com.trading.common.dto.order

import java.math.BigDecimal
import java.time.Instant

data class OrderDTO(
    val orderId: String,

    val userId: String,

    val symbol: String,

    val orderType: OrderType,

    val side: OrderSide,

    val quantity: BigDecimal,

    val price: BigDecimal?,

    val createdAt: Instant,

    val updatedAt: Instant,

    val status: OrderStatus = OrderStatus.PENDING,

    val traceId: String,

    val version: Long = 0
)
