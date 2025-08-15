package com.trading.common.dto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.math.BigDecimal
data class AccountDTO(
    @field:NotBlank
    val userId: String,

    @field:NotNull
    @field:PositiveOrZero
    val cashBalance: BigDecimal,

    @field:NotNull
    @field:PositiveOrZero
    val availableCash: BigDecimal
)
data class StockHoldingDTO(
    @field:NotBlank
    val userId: String,

    @field:NotBlank
    val symbol: String,

    @field:NotNull
    @field:PositiveOrZero
    val quantity: BigDecimal,

    @field:NotNull
    @field:PositiveOrZero
    val availableQuantity: BigDecimal,

    @field:NotNull
    @field:PositiveOrZero
    val averagePrice: BigDecimal
)
