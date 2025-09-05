package com.trading.common.event.saga

import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal
import java.time.Instant

data class AccountUpdatedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant = Instant.now(),
    override val traceId: String,
    val sagaId: String,
    val tradeId: String,
    val orderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val amount: BigDecimal,
    val quantity: BigDecimal,
    val symbol: String,
    val buyerNewBalance: BigDecimal,
    val sellerNewBalance: BigDecimal,
    val metadata: Map<String, Any>? = null
) : DomainEvent