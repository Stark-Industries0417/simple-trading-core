package com.trading.common.dto

import com.trading.order.domain.Order
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant


data class OrderDTO(
    @field:NotBlank
    val orderId: String,

    @field:NotBlank
    val userId: String,

    @field:NotBlank
    val symbol: String,

    @field:NotNull
    val orderType: OrderType,

    @field:NotNull
    val side: OrderSide,

    @field:NotNull
    @field:Positive
    val quantity: BigDecimal,

    val price: BigDecimal?,

    @field:NotNull
    val createdAt: Instant,

    @field:NotNull
    val updatedAt: Instant,

    @field:NotNull
    val status: OrderStatus = OrderStatus.PENDING,

    @field:NotBlank
    val traceId: String,

    val version: Long = 0
) {
    companion object {

        fun from(order: Order): OrderDTO {
            return OrderDTO(
                orderId = order.id,
                userId = order.userId,
                symbol = order.symbol,
                orderType = order.orderType,
                side = order.side,
                quantity = order.quantity,
                price = order.price,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt,
                status = order.status,
                traceId = order.traceId,
                version = order.version
            )
        }
    }
}
