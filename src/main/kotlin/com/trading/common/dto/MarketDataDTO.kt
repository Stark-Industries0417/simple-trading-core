package com.trading.common.dto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
data class MarketDataDTO(
    @field:NotBlank
    val symbol: String,

    @field:NotNull
    @field:Positive
    val price: BigDecimal,

    @field:NotNull
    @field:Positive
    val volume: BigDecimal,

    @field:NotNull
    val timestamp: Instant,

    val bid: BigDecimal? = null,
    val ask: BigDecimal? = null,
    val bidSize: BigDecimal? = null,
    val askSize: BigDecimal? = null
)
