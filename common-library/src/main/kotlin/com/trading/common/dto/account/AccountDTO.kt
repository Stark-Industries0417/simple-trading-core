package com.trading.common.dto.account
import java.math.BigDecimal
data class AccountDTO(
    val userId: String,

    val cashBalance: BigDecimal,

    val availableCash: BigDecimal
)
data class StockHoldingDTO(
    val userId: String,

    val symbol: String,

    val quantity: BigDecimal,

    val availableQuantity: BigDecimal,

    val averagePrice: BigDecimal
)
