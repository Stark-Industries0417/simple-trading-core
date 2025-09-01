package com.trading.common.dto.market
import java.math.BigDecimal
import java.time.Instant
data class MarketDataDTO(
    val symbol: String,

    val price: BigDecimal,

    val volume: BigDecimal,

    val timestamp: Instant,

    val bid: BigDecimal? = null,
    val ask: BigDecimal? = null,
    val bidSize: BigDecimal? = null,
    val askSize: BigDecimal? = null
)
