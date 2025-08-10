package com.trading.common.dto
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
    val ticker: String,

    @field:NotNull
    val orderType: OrderType,

    @field:NotNull
    val side: OrderSide,

    @field:NotNull
    @field:Positive
    val quantity: BigDecimal,

    val price: BigDecimal?,

    @field:NotNull
    val timestamp: Instant,

    val status: OrderStatus = OrderStatus.PENDING
)
