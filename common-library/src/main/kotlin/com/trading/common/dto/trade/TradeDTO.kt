package com.trading.common.dto.trade
import java.math.BigDecimal
import java.time.Instant


data class TradeDTO(
    val tradeId: String,

    val buyOrderId: String,

    val sellOrderId: String,

    val symbol: String,

    val quantity: BigDecimal,

    val price: BigDecimal,

    val executedAt: Instant,

    val buyUserId: String,

    val sellUserId: String
)
