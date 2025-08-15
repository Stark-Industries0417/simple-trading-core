package com.trading.common.dto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
data class TradeDTO(
    @field:NotBlank
    val tradeId: String,

    @field:NotBlank
    val buyOrderId: String,

    @field:NotBlank
    val sellOrderId: String,

    @field:NotBlank
    val symbol: String,

    @field:NotNull
    @field:Positive
    val quantity: BigDecimal,

    @field:NotNull
    @field:Positive
    val price: BigDecimal,

    @field:NotNull
    val executedAt: Instant,

    @field:NotBlank
    val buyUserId: String,

    @field:NotBlank
    val sellUserId: String
)
