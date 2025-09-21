package com.trading.common.dto.cdc.matching

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal


data class MatchingFailedDto(
    override val eventId: String,
    override val sagaId: String,
    val eventType: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val symbol: String,
    val quantity: BigDecimal,
    val price: BigDecimal?,
    val tradeId: String,
    val status: String,
    val processedAt: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val createdAt: String
) : DomainEvent