package com.trading.common.event.matching
import com.trading.common.event.base.DomainEvent
import java.math.BigDecimal
import java.time.Instant


data class OrderRejectedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val orderId: String,
    val userId: String,
    val symbol: String,
    val reason: String,
    val timestamp: Long
) : DomainEvent


data class TradeExecutedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val tradeId: String,
    val symbol: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val buyUserId: String,
    val sellUserId: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val timestamp: Long
) : DomainEvent


data class OrderPartiallyFilledEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val orderId: String,
    val userId: String,
    val symbol: String,
    val filledQuantity: BigDecimal,
    val remainingQuantity: BigDecimal,
    val averagePrice: BigDecimal
) : DomainEvent


data class MatchingEngineStateChangedEvent(
    override val eventId: String,
    override val aggregateId: String,
    override val occurredAt: Instant,
    override val traceId: String,
    val engineId: String,
    val previousState: String,
    val newState: String,
    val reason: String
) : DomainEvent